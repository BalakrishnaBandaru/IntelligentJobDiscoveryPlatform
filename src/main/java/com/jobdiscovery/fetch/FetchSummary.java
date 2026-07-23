package com.jobdiscovery.fetch;

import java.util.List;

/** Aggregate result of a fetch across all active sources. */
public record FetchSummary(String keywords, String location, List<SourceOutcome> sources,
                           int totalFetched, int totalSaved, int totalDuplicates) {
}
