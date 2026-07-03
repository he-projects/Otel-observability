package com.observability.commons.constant;

/** Shared Kafka serialization constants used by producer and consumer. */
public final class KafkaConstants {

    /** JSON field that stores the concrete event class name for polymorphic deserialization. */
    public static final String CLASS_PATH_KEY = "classPath";

    private KafkaConstants() {
    }
}
