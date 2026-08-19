package com.jobdiscovery.profile;

/**
 * Thrown when the candidate profile is requested but none has been set yet.
 * Surfaced to API callers as HTTP 404 by {@code ApiExceptionHandler}.
 */
public class ProfileNotFoundException extends RuntimeException {

    public ProfileNotFoundException() {
        super("No candidate profile has been set. Create one with POST /api/profile.");
    }
}
