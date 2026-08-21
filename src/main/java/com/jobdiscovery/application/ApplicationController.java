package com.jobdiscovery.application;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The application tracker (Phase 7). */
@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService service;

    public ApplicationController(ApplicationService service) {
        this.service = service;
    }

    /** Starts tracking a listing. Defaults to {@code APPLIED}. */
    @PostMapping
    public ApplicationView create(@Valid @RequestBody ApplicationRequest request) {
        return service.create(request);
    }

    /** @param status optional filter, e.g. {@code INTERVIEW} */
    @GetMapping
    public List<ApplicationView> list(@RequestParam(required = false) ApplicationStatus status) {
        return service.list(status);
    }

    /**
     * Counts per status, every status present even at zero.
     *
     * <p>Declared before {@code /{id}} so "funnel" is not parsed as an id.
     */
    @GetMapping("/funnel")
    public Map<ApplicationStatus, Long> funnel() {
        return service.funnel();
    }

    @GetMapping("/{id}")
    public ApplicationView get(@PathVariable Long id) {
        return service.get(id);
    }

    /** Partial update — omitted fields are left alone rather than blanked. */
    @PatchMapping("/{id}")
    public ApplicationView update(@PathVariable Long id,
                                  @Valid @RequestBody ApplicationUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
