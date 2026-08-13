package com.eventbooking.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {
    private Long id;
    private String name;
    private String email;
    private String role;
    private String authProvider; // frontend uses this to hide "change password" for GOOGLE accounts
    private String gender;
    private String phoneNumber;
    private String address;
    private String bio;
    private LocalDateTime createdAt;
}
