package com.eventbooking.service;

import com.eventbooking.exception.BadRequestException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

/**
 * Verifies the raw ID token string Google Identity Services hands to the
 * frontend after a successful "Sign in with Google" - NEVER trusts the
 * email/name claims inside it without first checking the token's signature
 * against Google's public keys and confirming it was actually issued for
 * OUR client id (the "audience" check below). Skipping either check would
 * let anyone forge a token claiming to be any email address.
 */
@Service
@Slf4j
public class GoogleTokenVerifierService {

    @Value("${app.google.client-id}")
    private String googleClientId;

    private GoogleIdTokenVerifier verifier;

    private GoogleIdTokenVerifier getVerifier() {
        // Built lazily rather than in a constructor/@PostConstruct so a
        // missing/blank client id only breaks Google login specifically
        // (surfaced as a clear error the moment someone tries it), instead
        // of blowing up the entire application context on startup for
        // people who aren't using Google login at all.
        if (verifier == null) {
            verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();
        }
        return verifier;
    }

    public GoogleUserInfo verify(String idTokenString) {
        if (googleClientId == null || googleClientId.isBlank()) {
            throw new BadRequestException("Google Sign-In is not configured on this server.");
        }

        try {
            GoogleIdToken idToken = getVerifier().verify(idTokenString);
            if (idToken == null) {
                throw new BadRequestException("Invalid Google credential. Please try signing in again.");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();

            Boolean emailVerified = payload.getEmailVerified();
            if (emailVerified == null || !emailVerified) {
                // Google itself flags whether the email on the account was
                // ever confirmed - if Google won't vouch for it, neither will we.
                throw new BadRequestException("Your Google account's email is not verified.");
            }

            String email = payload.getEmail();
            String name = (String) payload.get("name");

            return new GoogleUserInfo(email, name != null ? name : email);
        } catch (GeneralSecurityException | java.io.IOException e) {
            log.error("Google ID token verification failed: {}", e.getMessage());
            throw new BadRequestException("Could not verify Google credential. Please try again.");
        }
    }

    @Getter
    @RequiredArgsConstructor
    public static class GoogleUserInfo {
        private final String email;
        private final String name;
    }
}
