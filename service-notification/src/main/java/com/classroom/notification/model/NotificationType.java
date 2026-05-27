package com.classroom.notification.model;

/**
 * Discriminates between the two types of notification the service can send.
 */
public enum NotificationType {

    /** A booking was successfully confirmed by the availability service. */
    CONFIRMATION,

    /** A booking was rejected due to a time-slot conflict. */
    REJECTION
}

