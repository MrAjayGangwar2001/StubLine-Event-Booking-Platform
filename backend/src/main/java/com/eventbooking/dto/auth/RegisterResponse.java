package com.eventbooking.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Returned from POST /register instead of AuthResponse - deliberately does
 * NOT include a JWT, since the account isn't usable yet until the OTP sent
 * to their email is verified. Forcing this shape (rather than returning a
 * token here and just hoping the frontend doesn't use it) makes "unverified
 * accounts can't log in" structurally true, not just a convention someone
 * could accidentally bypass.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterResponse {
    private String email;
    private String message;
}
