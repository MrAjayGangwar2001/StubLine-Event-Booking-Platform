package com.eventbooking.entity;

public enum AuthProvider {
    LOCAL,   // registered with email + password, verified via OTP
    GOOGLE   // signed in via Google - email already verified by Google, no password
}
