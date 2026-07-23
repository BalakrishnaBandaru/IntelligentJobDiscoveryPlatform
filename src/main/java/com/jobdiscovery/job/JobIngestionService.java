package com.jobdiscovery.job;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Shared persistence step for every source: computes a content hash for each
 * fetched listing and stores only the ones we haven't seen before.
 *
 * <p>De-dup key = SHA-256 of the normalised {@code (title | company | location)}.
 * We hash content rather than the apply URL because the same posting has
 * different URLs across sources (and some Adzuna URLs carry volatile tracking
 * params), so a URL constraint would let duplicates through — while a content
 * hash also catches the same role surfacing from two different sources.
 */
@Service
public class JobIngestionService {

    private final JobListingRepository repository;

    public JobIngestionService(JobListingRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public IngestionResult ingest(List<JobListing> fetched) {
        int total = fetched.size();

        // 1. Drop anything we can't meaningfully store or hash.
        List<JobListing> valid = fetched.stream()
                .filter(j -> StringUtils.hasText(j.getTitle()) && StringUtils.hasText(j.getApplyUrl()))
                .toList();
        int invalid = total - valid.size();

        // 2. Assign each listing's content hash.
        valid.forEach(j -> j.setContentHash(contentHash(j)));

        // 3. De-duplicate within this batch (first occurrence wins).
        Map<String, JobListing> byHash = new LinkedHashMap<>();
        for (JobListing job : valid) {
            byHash.putIfAbsent(job.getContentHash(), job);
        }

        // 4. De-duplicate against what's already stored.
        Set<String> alreadyStored = byHash.isEmpty()
                ? Set.of()
                : new HashSet<>(repository.findExistingHashes(byHash.keySet()));

        List<JobListing> toSave = byHash.values().stream()
                .filter(job -> !alreadyStored.contains(job.getContentHash()))
                .toList();

        repository.saveAll(toSave);

        int saved = toSave.size();
        int duplicates = valid.size() - saved;
        return new IngestionResult(total, saved, duplicates, invalid);
    }

    /** Hash of the normalised business fields that identify a posting. */
    private String contentHash(JobListing job) {
        String basis = normalise(job.getTitle())
                + "|" + normalise(job.getCompany())
                + "|" + normalise(job.getLocation());
        return sha256Hex(basis);
    }

    /**
     * Lower-cased, trimmed, internal-whitespace-collapsed — so trivial
     * formatting differences don't defeat de-duplication.
     */
    private String normalise(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be present on every JVM.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
