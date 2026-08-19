package com.jobdiscovery.profile;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manage the candidate profile that drives Phase 5 scoring. Single-user tool:
 * there is one profile; {@code POST} creates it or replaces it in place.
 */
@RestController
@RequestMapping("/api/profile")
public class CandidateProfileController {

    private final CandidateProfileService service;

    public CandidateProfileController(CandidateProfileService service) {
        this.service = service;
    }

    /** Create the profile, or replace it if one already exists. */
    @PostMapping
    public CandidateProfile save(@Valid @RequestBody CandidateProfileRequest request) {
        return service.save(request);
    }

    /** The current profile; 404 if none has been set yet. */
    @GetMapping
    public CandidateProfile get() {
        return service.get();
    }

    /** Clear the profile (handy for re-seeding). */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete() {
        service.delete();
    }
}
