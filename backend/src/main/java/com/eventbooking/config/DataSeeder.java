package com.eventbooking.config;

import com.eventbooking.entity.Role;
import com.eventbooking.entity.User;
import com.eventbooking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds a default SUPER_ADMIN account on local startup so there's always a
 * way in without manually flipping a role in the DB. Self-registration
 * (/api/auth/register) always creates USER accounts - this is intentional,
 * see AuthService. Only SUPER_ADMIN can promote/demote other users
 * (AdminUserController) - a regular ADMIN cannot create more admins.
 *
 * If this seeder already ran against your database BEFORE this class
 * seeded SUPER_ADMIN instead of ADMIN, the existing row won't be touched
 * (see the existsByEmail early-return below) - run this once by hand:
 *   UPDATE users SET role = 'SUPER_ADMIN' WHERE email = 'admin@eventbooking.com';
 *
 * Change ADMIN_PASSWORD before deploying anywhere beyond localhost.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private static final String ADMIN_EMAIL = "admin@eventbooking.com";
    private static final String ADMIN_PASSWORD = "Admin@123";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.existsByEmail(ADMIN_EMAIL)) {
            return;
        }

        User admin = User.builder()
                .name("Platform Admin")
                .email(ADMIN_EMAIL)
                .passwordHash(passwordEncoder.encode(ADMIN_PASSWORD))
                .role(Role.SUPER_ADMIN)
                .emailVerified(true) // seeded accounts skip OTP verification - there's no inbox to check
                .authProvider(com.eventbooking.entity.AuthProvider.LOCAL)
                .build();

        userRepository.save(admin);
        log.info("Seeded default admin account -> email: {}, password: {}", ADMIN_EMAIL, ADMIN_PASSWORD);
    }
}
