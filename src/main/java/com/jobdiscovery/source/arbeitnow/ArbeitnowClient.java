package com.jobdiscovery.source.arbeitnow;

import com.jobdiscovery.source.SourceApiException;
import com.jobdiscovery.source.arbeitnow.dto.ArbeitnowResponse;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Client for the Arbeitnow open job-board API — a simple, unauthenticated GET
 * returning a feed of recent postings (no server-side keyword/location search).
 */
@Component
public class ArbeitnowClient {

    private final RestClient restClient;

    public ArbeitnowClient(ArbeitnowProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(15_000);
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public ArbeitnowResponse fetchBoard() {
        try {
            return restClient.get()
                    .uri("/job-board-api")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(ArbeitnowResponse.class);
        } catch (RestClientResponseException e) {
            throw new SourceApiException("ARBEITNOW",
                    "Arbeitnow returned HTTP %d. Body: %s"
                            .formatted(e.getStatusCode().value(), e.getResponseBodyAsString()), e);
        } catch (RestClientException e) {
            throw new SourceApiException("ARBEITNOW", "Could not reach the Arbeitnow API: " + e.getMessage(), e);
        }
    }
}
