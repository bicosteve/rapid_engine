package com.bixx.rapid_engine.messaging;

public class EventPublishException extends RuntimeException {
public EventPublishException(String message) {
super(message);
}

public EventPublishException(String message, Throwable cause) {
super(message, cause);
}
}
