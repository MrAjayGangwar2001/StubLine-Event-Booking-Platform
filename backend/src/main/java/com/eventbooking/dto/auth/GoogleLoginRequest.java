package com.eventbooking.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GoogleLoginRequest {

    // The raw ID token JWT string from Google Identity Services on the
    // frontend - NOT an access token, and never trusted as-is; the backend
    // verifies its signature and claims against Google's public keys before
    // trusting anything in it (see GoogleTokenVerifierService).
    @NotBlank(message = "Google credential is required")
    private String credential;
}
