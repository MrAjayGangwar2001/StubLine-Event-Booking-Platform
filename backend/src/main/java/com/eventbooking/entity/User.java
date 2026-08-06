package com.eventbooking.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    // Nullable now - a GOOGLE-provider account never sets a local password,
    // since Google already authenticated them. Password-login is explicitly
    // rejected in AuthService for such accounts, rather than ever attempting
    // to match against a null hash.
    @Column(nullable = true)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    // Whether this email address has been proven to belong to this person -
    // via OTP for LOCAL accounts, or implicitly true for GOOGLE accounts
    // (Google already verified it on their end before ever handing us an ID token).
    @Builder.Default
    @Column(nullable = false)
    private boolean emailVerified = false;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 20)
    private AuthProvider authProvider = AuthProvider.LOCAL;

    // Optional profile details - deliberately never asked for at signup
    // (register() only ever sets name/email/password), only ever filled in
    // later via PUT /api/users/me if the person chooses to.
    @Column(length = 20)
    private String gender;

    @Column(length = 20)
    private String phoneNumber;

    @Column(length = 255)
    private String address;

    @Column(length = 1000)
    private String bio;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // ---- UserDetails contract (used directly by Spring Security) ----

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (role == Role.SUPER_ADMIN) {
            // A super admin should still be able to do everything a normal
            // ADMIN can (manage events, view analytics, etc.) - granting
            // both authorities means every existing hasRole('ADMIN') check
            // keeps working unchanged, rather than needing every one of
            // them rewritten to hasAnyRole('ADMIN','SUPER_ADMIN').
            return List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"), new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    /**
     * Ties email verification directly into Spring Security's standard login
     * flow: DaoAuthenticationProvider automatically rejects an unverified
     * user with a DisabledException before it even checks the password,
     * without needing a bespoke check scattered elsewhere. AuthService
     * catches that specific exception to give a clear "verify your email
     * first" message instead of a generic auth failure.
     */
    @Override
    public boolean isEnabled() { return emailVerified; }
}
