package com.eventbooking.config;

import com.eventbooking.entity.Role;
import com.eventbooking.entity.User;
import com.eventbooking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;



@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    @Value("${app.admin.ADMIN_EMAIL}")
    private String ADMIN_EMAIL;

    @Value("${app.admin.ADMIN_PASSWORD}")
    private String ADMIN_PASSWORD;

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
