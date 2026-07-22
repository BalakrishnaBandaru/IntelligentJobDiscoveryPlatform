package com.jobdiscovery.source.adzuna;

import com.jobdiscovery.source.adzuna.dto.AdzunaSearchResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Thin HTTP client over the Adzuna "search" endpoint.
 *
 * <p>Adzuna is a GET API:
 * {@code GET {base}/jobs/{country}/search/{page}} with {@code app_id},
 * {@code app_key} and the search terms passed as query parameters.
 */
@Component
public class AdzunaClient {

    private final RestClient restClient;
    private final AdzunaProperties properties;

    public AdzunaClient(AdzunaProperties properties) {
        this.properties = properties;

        // Fail fast rather than hang the request thread if Adzuna is slow/unreachable.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(15_000);

        // Spring Boot 4.x doesn't expose an auto-configured RestClient.Builder
        // bean in this setup, so build from the static factory. It initialises
        // the default message converters (including Jackson for JSON).
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /** Deserialised response, used by the import flow. */
    public AdzunaSearchResponse search(String what, String where, int page) {
        return execute(what, where, page, AdzunaSearchResponse.class);
    }

    /**
     * Raw JSON body, used by the inspection endpoint so the exact response
     * structure can be examined without our mapping getting in the way.
     */
    public String searchRaw(String what, String where, int page) {
        return execute(what, where, page, String.class);
    }

    private <T> T execute(String what, String where, int page, Class<T> responseType) {
        // Guard against the most common local-setup mistake: keys not set.
        if (!StringUtils.hasText(properties.appId()) || !StringUtils.hasText(properties.appKey())) {
            throw new AdzunaClientException(
                    "Adzuna credentials are not configured. Set ADZUNA_APP_ID and ADZUNA_APP_KEY "
                    + "in your .env file, then rebuild/restart the app container.");
        }

        try {
            return restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder
                                .path("/jobs/{country}/search/{page}")
                                .queryParam("app_id", properties.appId())
                                .queryParam("app_key", properties.appKey())
                                .queryParam("results_per_page", properties.resultsPerPage())
                                .queryParam("what", what)
                                .queryParam("where", where)
                                .queryParam("content-type", "application/json");
                        // Recency filter + ordering, both configurable. Adding
                        // max_days_old keeps stale/expired postings out; sort_by=date
                        // returns newest first.
                        if (properties.maxDaysOld() > 0) {
                            uriBuilder.queryParam("max_days_old", properties.maxDaysOld());
                        }
                        if (StringUtils.hasText(properties.sortBy())) {
                            uriBuilder.queryParam("sort_by", properties.sortBy());
                        }
                        return uriBuilder.build(properties.country(), page);
                    })
                    .retrieve()
                    .body(responseType);
        } catch (RestClientResponseException e) {
            // Adzuna answered with a 4xx/5xx (e.g. 401 for bad keys).
            throw new AdzunaClientException(
                    "Adzuna returned HTTP %d. Check ADZUNA_APP_ID/ADZUNA_APP_KEY and the query params. Body: %s"
                            .formatted(e.getStatusCode().value(), e.getResponseBodyAsString()), e);
        } catch (RestClientException e) {
            // Never got a valid HTTP response (timeout, DNS failure, connection refused).
            throw new AdzunaClientException("Could not reach the Adzuna API: " + e.getMessage(), e);
        }
    }
}
