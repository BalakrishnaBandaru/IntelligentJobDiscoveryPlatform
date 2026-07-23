package com.jobdiscovery.source.jooble;

import com.jobdiscovery.source.SourceApiException;
import com.jobdiscovery.source.jooble.dto.JoobleRequest;
import com.jobdiscovery.source.jooble.dto.JoobleResponse;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Client for the Jooble search API.
 *
 * <p>Jooble is a POST API where the key is part of the URL path:
 * {@code POST https://jooble.org/api/{key}} with a JSON body
 * {@code {"keywords": "...", "location": "..."}}.
 */
@Component
public class JoobleClient {

    private final RestClient restClient;
    private final JoobleProperties properties;

    public JoobleClient(JoobleProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(15_000);
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public JoobleResponse search(String keywords, String location) {
        if (!StringUtils.hasText(properties.apiKey())) {
            throw new SourceApiException("JOOBLE",
                    "Jooble API key is not configured. Set JOOBLE_API_KEY in your .env file, "
                    + "then rebuild/restart the app container.");
        }
        try {
            return restClient.post()
                    .uri("/{key}", properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(new JoobleRequest(keywords, location))
                    .retrieve()
                    .body(JoobleResponse.class);
        } catch (RestClientResponseException e) {
            throw new SourceApiException("JOOBLE",
                    "Jooble returned HTTP %d. Check JOOBLE_API_KEY. Body: %s"
                            .formatted(e.getStatusCode().value(), e.getResponseBodyAsString()), e);
        } catch (RestClientException e) {
            throw new SourceApiException("JOOBLE", "Could not reach the Jooble API: " + e.getMessage(), e);
        }
    }
}
