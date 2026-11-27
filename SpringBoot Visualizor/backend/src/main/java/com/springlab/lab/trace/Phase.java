package com.springlab.lab.trace;

/** Where in the request a call happened, relative to the moment DispatcherServlet entered a controller method. */
public enum Phase {
    /** Before any controller was entered: inside the Spring Security filter chain. */
    SECURITY,
    /** After the controller handler was entered. */
    HANDLER
}
