package com.jobdiscovery.application;

/** A tracker operation that cannot be carried out. */
public class ApplicationException extends RuntimeException {

    /** No such application, or no such job listing. */
    public static final String NOT_FOUND = "application_not_found";

    /** An application already exists for that listing. */
    public static final String ALREADY_TRACKED = "application_already_exists";

    private final String code;

    public ApplicationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
