package com.eventbooking.controller;

import com.eventbooking.dto.auth.ChangePasswordRequest;
import com.eventbooking.dto.auth.SimpleMessageResponse;
import com.eventbooking.dto.auth.UpdateProfileRequest;
import com.eventbooking.dto.auth.UserProfileResponse;
import com.eventbooking.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<UserProfileResponse> getMyProfile() {
        return ResponseEntity.ok(userService.getMyProfile());
    }

    @PutMapping
    public ResponseEntity<UserProfileResponse> updateMyProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateMyProfile(request));
    }

    @PutMapping("/password")
    public ResponseEntity<SimpleMessageResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(request);
        return ResponseEntity.ok(SimpleMessageResponse.builder().message("Password updated.").build());
    }
}
