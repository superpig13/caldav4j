/*
 * Copyright 2011 Open Source Applications Foundation
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.osaf.caldav4j.methods;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Calendar;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpConnection;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.webdav.lib.methods.DepthSupport;
import org.apache.webdav.lib.methods.HttpRequestBodyMethodBase;
import org.apache.webdav.lib.util.XMLDebugOutputer;
import org.osaf.caldav4j.CalDAVConstants;
import org.osaf.caldav4j.exceptions.CalDAV4JException;
import org.osaf.caldav4j.exceptions.CalDAV4JProtocolException;
import org.osaf.caldav4j.exceptions.DOMValidationException;
import org.osaf.caldav4j.model.request.CalDAVReportRequest;
import org.osaf.caldav4j.util.UrlUtils;
import org.osaf.caldav4j.util.XMLUtils;
import org.w3c.dom.Document;

import java.io.IOException;
import java.io.InputStream;

/**
 * This method implements the REPORT method described in 
 *  caldav RFC4791. As it's requestBody is an XML document
 *  it takes as a parameter a class describing the document, then
 *  generates the body before passing it to executeMethod.
 *  
 *  so:
 *  1- set this.reportRequest 
 *  2- the class creates the body from the XML
 * 
 * This method differs from {@code CalDAVReportMethod} in that its response is expected to be a calendar, rather than
 * XML.
 *  
 * @author robipolli@gmail.com
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @see CalDAVReportMethod
 */
public class CalendarCalDAVReportMethod extends HttpRequestBodyMethodBase implements CalDAVConstants {
	
	// TODO: rationalise with CalDAVReportMethod
	
    private static final Log log = LogFactory
        .getLog(CalendarCalDAVReportMethod.class);
    
    // debug level 
    private int debug = 0;

    /**
     * XML Debug Outputter
     */
    private XMLDebugOutputer xo = new XMLDebugOutputer();
    
    private CalendarBuilder calendarBuilder = null;

    public CalendarBuilder getCalendarBuilder() {
		return calendarBuilder;
	}

	public void setCalendarBuilder(CalendarBuilder calendarBuilder) {
		this.calendarBuilder = calendarBuilder;
	}

	/** this is the XML document that will be generated by @link{generateRequestBody()} */
    private CalDAVReportRequest reportRequest; 
    
    private int depth = DEPTH_1;
    
    protected CalendarCalDAVReportMethod() {

    }

    protected CalendarCalDAVReportMethod(String path, CalDAVReportRequest reportRequest) {
        this.reportRequest = reportRequest;
        setPath(path);
    }

    /**
     * Depth setter.
     *
     * @param depth New depth value
     */
    public void setDepth(int depth) {
        this.depth = depth;
    }

    /**
     * Depth getter.
     *
     * @return int depth value
     */
    public int getDepth() {
        return depth;
    }
    
    @Override
	public String getName() {
        return CalDAVConstants.METHOD_REPORT;
    }

    public CalDAVReportRequest getReportRequest() {
        return reportRequest;
    }

    public void setReportRequest(CalDAVReportRequest reportRequest) {
        this.reportRequest = reportRequest;
    }
    
    
    /**
     * Generate additional headers needed by the request.
     *
     * @param state State token
     * @param conn The connection being used to make the request.
     */
    @Override
	public void addRequestHeaders(HttpState state, HttpConnection conn)
    throws IOException, HttpException {

        super.addRequestHeaders(state, conn);

        switch (depth) {
        case DEPTH_0:
            super.setRequestHeader("Depth", "0");
            break;
        case DEPTH_1:
            super.setRequestHeader("Depth", "1");
            break;
        case DEPTH_INFINITY:
            super.setRequestHeader("Depth", CalDAVConstants.INFINITY_STRING);
            break;
        }

        if (getRequestHeader(HEADER_CONTENT_TYPE) == null) {
        	addRequestHeader(HEADER_CONTENT_TYPE, CONTENT_TYPE_TEXT_XML);
        }
    }

    // TODO: centralise; copied from XMLResponseMethodBase
    /**
     * Return the length (in bytes) of my request body, suitable for use in a
     * <tt>Content-Length</tt> header.
     *
     * <p>
     * Return <tt>-1</tt> when the content-length is unknown.
     * </p>
     *
     * <p>
     * This implementation returns <tt>0</tt>, indicating that the request has
     * no body.
     * </p>
     *
     * @return <tt>0</tt>, indicating that the request has no body.
     */
    @Override
	protected int getRequestContentLength() {
        if (!isRequestContentAlreadySet()) {
            String contents = generateRequestBody();
            // be nice - allow overriding functions to return null or empty
            // strings for no content.
            if (contents == null)
                contents = "";

            setRequestBody(contents);
            

            if (debug > 0) {
                System.out.println("\n>>>>>>>  to  server  ---------------------------------------------------");
				System.out.println(getName() + " " +
				   getPath() + (getQueryString() != null ? "?" + getQueryString() : "") + " " + "HTTP/1.1");
        
				   Header[] headers = getRequestHeaders();
				   for (int i = 0; i < headers.length; i++) {
					   Header header = headers[i];
					   System.out.print(header.toString());
				   }
				System.out.println("Content-Length: "+super.getRequestContentLength());
				   
				if (this instanceof DepthSupport) {
					System.out.println("Depth: "+((DepthSupport)this).getDepth());
				}

                System.out.println();
                xo.print(contents);
                System.out.println("------------------------------------------------------------------------");
            }

        }

        return super.getRequestContentLength();
    }

    /**
     * Generates a request body from the calendar query.
     */
    protected String generateRequestBody() {
        Document doc = null;
        try {
            doc = reportRequest.createNewDocument(XMLUtils
                    .getDOMImplementation());
        } catch (DOMValidationException domve) {
            log.error("Error trying to create DOM from CalDAVReportRequest: ", domve);
            throw new RuntimeException(domve);
        }
        return XMLUtils.toPrettyXML(doc);
    }
    
    // TODO: centralise; copied from GetMethod
    public Calendar getResponseBodyAsCalendar()  throws
		    ParserException, CalDAV4JException {
		Calendar ret = null;
		InputStream stream = null;
		try {
		    Header header = getResponseHeader(CalDAVConstants.HEADER_CONTENT_TYPE);
		    String contentType = header.getValue();
		    if (contentType.startsWith(CalDAVConstants.CONTENT_TYPE_CALENDAR)) {
		        stream = getResponseBodyAsStream();
		        ret =  calendarBuilder.build(stream);
		        return ret;		        
		    }
		
		    log.error("Expected content-type text/calendar. Was: " + contentType);
		    throw new CalDAV4JProtocolException("Expected content-type text/calendar. Was: " + contentType );
		} catch (IOException e) {
			if (stream != null ) { //the server sends the response
				if (log.isWarnEnabled()) {
					log.warn("Server response is " + UrlUtils.parseISToString(stream));
				}
			}
			throw new CalDAV4JException("Error retrieving and parsing server response at " + getPath(), e);
		}	       
	}
    
    // remove double slashes
    @Override
	public void setPath(String path) {
    	super.setPath(UrlUtils.removeDoubleSlashes(path));
    }
}