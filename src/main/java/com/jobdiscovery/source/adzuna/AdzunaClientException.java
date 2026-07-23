package com.jobdiscovery.source.adzuna;

import com.jobdiscovery.source.SourceApiException;

/**
 * Thrown when a call to the Adzuna API fails. A specialisation of
 * {@link SourceApiException}; surfaced to callers as HTTP 502.
 */
public class AdzunaClientException extends SourceApiException {

    public AdzunaClientException(String message) {
        super("ADZUNA", message);
    }

    public AdzunaClientException(String message, Throwable cause) {
        super("ADZUNA", message, cause);
    }
}
