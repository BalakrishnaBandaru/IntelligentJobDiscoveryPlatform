package com.jobdiscovery.notify;

import java.util.Map;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Thin HTTP client over the Telegram Bot API, built like every other client in
 * this project — a plain {@link RestClient} against a documented REST endpoint.
 *
 * <p>The bot token is part of the URL path
 * ({@code /bot<token>/sendMessage}), which is the same shape Jooble uses. It is
 * therefore kept out of logs: an error message here quotes the response body,
 * never the request URL.
 */
@Component
public class TelegramClient {

    private final RestClient restClient;
    private final TelegramProperties properties;

    public TelegramClient(TelegramProperties properties) {
        this.properties = properties;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(15_000);

        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /** True when the feature is on and both the token and chat id are present. */
    public boolean isConfigured() {
        return properties.enabled()
                && StringUtils.hasText(properties.botToken())
                && StringUtils.hasText(properties.chatId());
    }

    /**
     * Sends one message.
     *
     * @param html body in Telegram's HTML parse mode — see
     *             {@link DigestFormatter} for the escaping rules
     * @throws NotificationException if unconfigured, rejected, or unreachable
     */
    public void sendMessage(String html) {
        if (!isConfigured()) {
            throw new NotificationException(NotificationException.NOT_CONFIGURED,
                    "Telegram is not configured. Create a bot with @BotFather, then set "
                    + "TELEGRAM_BOT_TOKEN, TELEGRAM_CHAT_ID and TELEGRAM_ENABLED=true in "
                    + "your .env file and recreate the app container.");
        }

        Map<String, Object> body = Map.of(
                "chat_id", properties.chatId(),
                "text", html,
                "parse_mode", "HTML",
                // The digest already carries the apply links; letting Telegram
                // expand a preview card for the first one buries the rest.
                "disable_web_page_preview", true);

        try {
            restClient.post()
                    .uri("/bot{token}/sendMessage", properties.botToken())
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            // Telegram puts the useful part in "description", e.g. "chat not
            // found" or "can't parse entities". Quote the body, never the URL —
            // the token is in the URL.
            throw new NotificationException(NotificationException.UPSTREAM_FAILED,
                    "Telegram returned HTTP %d: %s".formatted(
                            e.getStatusCode().value(), e.getResponseBodyAsString()), e);
        } catch (RestClientException e) {
            throw new NotificationException(NotificationException.UPSTREAM_FAILED,
                    "Could not reach the Telegram API: " + e.getMessage(), e);
        }
    }
}
