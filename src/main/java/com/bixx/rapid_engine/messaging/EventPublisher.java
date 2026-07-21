package com.bixx.rapid_engine.messaging;

import com.bixx.rapid_engine.models.Event;

public interface EventPublisher {
    void publish(EventChannel channel, Event event);
}
