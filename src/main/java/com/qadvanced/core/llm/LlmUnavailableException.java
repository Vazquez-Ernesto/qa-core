package com.qadvanced.core.llm;

/** No configured provider produced an answer. Mapped to HTTP 503. */
public class LlmUnavailableException extends RuntimeException {
    public LlmUnavailableException(String message) {
        super(message);
    }
}
