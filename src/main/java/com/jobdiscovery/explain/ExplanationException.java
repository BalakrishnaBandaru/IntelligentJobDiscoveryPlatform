package com.jobdiscovery.explain;

/**
 * Something went wrong producing an explanation — no key configured, the API
 * refused, or it could not be reached.
 *
 * <p>Carries a machine-readable {@code code} so
 * {@link com.jobdiscovery.web.ApiExceptionHandler} can map it to a sensible
 * status without string-matching the message.
 */
public class ExplanationException extends RuntimeException {

    /** No API key, or the feature is switched off. Caller's setup problem. */
    public static final String NOT_CONFIGURED = "explanations_not_configured";

    /** The model declined the request, or we could not reach the API. */
    public static final String UPSTREAM_FAILED = "explanation_upstream_error";

    private final String code;

    public ExplanationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public ExplanationException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
