package com.jobdiscovery.notify;

/** Something went wrong sending a digest. */
public class NotificationException extends RuntimeException {

    /** No bot token or chat id. The caller's setup problem. */
    public static final String NOT_CONFIGURED = "telegram_not_configured";

    /** Telegram rejected the message, or could not be reached. */
    public static final String UPSTREAM_FAILED = "telegram_send_failed";

    private final String code;

    public NotificationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public NotificationException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
