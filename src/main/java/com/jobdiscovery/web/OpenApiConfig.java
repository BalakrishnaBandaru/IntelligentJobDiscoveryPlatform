package com.jobdiscovery.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Describes the API for Swagger UI, served at {@code /swagger-ui/index.html}.
 *
 * <p>Only the document metadata lives here. The paths and schemas are derived
 * from the controllers by springdoc, so this file cannot drift out of step with
 * the actual endpoints — which is the whole reason for generating the spec
 * rather than writing one.
 *
 * <p>Tags are declared in pipeline order rather than alphabetically, so the page
 * reads the way the system works: fetch, then profile, then rank, then act.
 */
@Configuration
public class OpenApiConfig {

    private static final String DESCRIPTION = """
            Aggregates job listings from multiple public APIs, de-duplicates \
            them, ranks them against a candidate profile with a deterministic \
            rule engine, explains the ranking, and notifies via Telegram.

            **The score is produced by code, not by a model.** The LLM layer \
            only puts an existing score into words — it can neither produce nor \
            adjust the number. That split is what keeps the ranking auditable \
            and reproducible.

            Most features degrade rather than fail: with no LLM configured the \
            explanations fall back to a deterministic templated writer, and with \
            no Telegram bot the ranking is unaffected.
            """;

    @Bean
    public OpenAPI jobDiscoveryOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Intelligent Job Discovery Platform")
                        .version("0.0.1-SNAPSHOT")
                        .description(DESCRIPTION)
                        .license(new License().name("Personal portfolio project")))
                .tags(List.of(
                        new Tag().name("Fetch")
                                .description("Pull listings from the source APIs, and the "
                                        + "history of every run"),
                        new Tag().name("Jobs")
                                .description("The stored listings"),
                        new Tag().name("Profile")
                                .description("The candidate profile that ranking scores "
                                        + "against. A singleton"),
                        new Tag().name("Matches")
                                .description("The ranked shortlist, with the per-dimension "
                                        + "breakdown behind each score"),
                        new Tag().name("Notifications")
                                .description("The Telegram digest, and a preview that needs "
                                        + "no bot token"),
                        new Tag().name("Applications")
                                .description("Which jobs have been applied to, and what "
                                        + "happened next")));
    }
}
