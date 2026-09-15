package com.ethanlally.skyblocknexus.http;

public class UpstreamResponseException extends RuntimeException {

    public UpstreamResponseException(String service) {
        super(service + " returned an unsuccessful or malformed response. Please try again later.");
    }
}
