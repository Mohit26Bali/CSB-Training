package com.csb.cinescope.exception;

import org.springframework.http.HttpStatus;

/**
 * TMDB could not answer: it is down, too slow, rate-limiting us or refused our token (502 / 503 / 504). The message is
 * safe to show to users; technical details are logged by {@code tmdb/TmdbHttpGateway}, never sent to the client.
 */
public class UpstreamException extends AppException {

    public UpstreamException(HttpStatus status, String message) {
        super(status, message);
    }
}
