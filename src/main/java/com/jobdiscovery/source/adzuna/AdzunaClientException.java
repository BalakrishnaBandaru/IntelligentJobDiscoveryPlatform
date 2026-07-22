package com.jobdiscovery.source.adzuna;

/**
 * Thrown when a call to the Adzuna API fails — missing/invalid credentials, an
 * upstream error, or a network problem. Surfaced to API callers as HTTP 502 by
 * {@code ApiExceptionHandler}.
 */
public class AdzunaClientException extends RuntimeException {

    public AdzunaClientException(String message) {
        super(message);
    }

    public AdzunaClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
