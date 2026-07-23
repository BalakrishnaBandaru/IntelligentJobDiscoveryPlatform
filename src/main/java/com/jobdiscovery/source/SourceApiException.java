package com.jobdiscovery.source;

/**
 * Thrown when a call to an external job-source API fails — missing/invalid
 * credentials, an upstream error, or a network problem. Surfaced to API callers
 * as HTTP 502 by {@code ApiExceptionHandler}.
 */
public class SourceApiException extends RuntimeException {

    private final String source;

    public SourceApiException(String source, String message) {
        super(message);
        this.source = source;
    }

    public SourceApiException(String source, String message, Throwable cause) {
        super(message, cause);
        this.source = source;
    }

    /** Which source failed, e.g. "ADZUNA" / "JOOBLE" / "ARBEITNOW". */
    public String getSource() {
        return source;
    }
}
