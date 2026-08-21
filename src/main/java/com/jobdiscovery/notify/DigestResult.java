package com.jobdiscovery.notify;

import java.util.List;

/**
 * What a digest run did.
 *
 * @param sent     false when there was nothing new worth sending, which is a
 *                 normal outcome rather than a failure
 * @param jobCount how many matches went out
 * @param topScore the best score in the digest, or 0 when nothing was sent
 * @param jobIds   the listings now marked notified
 */
public record DigestResult(boolean sent, int jobCount, double topScore, List<Long> jobIds) {

    public static DigestResult nothingToSend() {
        return new DigestResult(false, 0, 0.0, List.of());
    }
}
