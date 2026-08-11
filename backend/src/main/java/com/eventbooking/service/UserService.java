package com.eventbooking.service;

import com.eventbooking.dto.auth.AdminUserResponse;
import com.eventbooking.dto.auth.ChangePasswordRequest;
import com.eventbooking.dto.auth.UpdateProfileRequest;
import com.eventbooking.dto.auth.UserProfileResponse;
import com.eventbooking.entity.Role;
import com.eventbooking.entity.User;
import com.eventbooking.exception.BadRequestException;
import com.eventbooking.exception.ResourceNotFoundException;
import com.eventbooking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserProfileResponse getMyProfile() {
        return toResponse(getCurrentUser());
    }

    @Transactional
    public UserProfileResponse updateMyProfile(UpdateProfileRequest request) {
        User user = getCurrentUser();
        user.setName(request.getName());
        user.setGender(request.getGender());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setAddress(request.getAddress());
        user.setBio(request.getBio());
        userRepository.save(user);
        return toResponse(user);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = getCurrentUser();

        if (user.getPasswordHash() == null) {
            throw new BadRequestException("This account signs in with Google and has no password yet. Use \"Forgot password\" to set one first.");
        }

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    private User getCurrentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    // --- Admin user-management (SUPER_ADMIN only - enforced in
    // AdminUserController via @PreAuthorize, not repeated here) ---

    public List<AdminUserResponse> searchUsers(String search) {
        List<User> users = (search == null || search.isBlank())
                ? userRepository.findAll()
                : userRepository.searchByEmailOrName(search.trim());
        return users.stream().map(this::toAdminUserResponse).toList();
    }

    @Transactional
    public AdminUserResponse promoteToAdmin(Long userId) {
        User user = findUserOrThrow(userId);
        if (user.getRole() == Role.SUPER_ADMIN) {
            throw new BadRequestException("This user is already a super admin");
        }
        if (user.getRole() == Role.ADMIN) {
            throw new BadRequestException("This user is already an admin");
        }
        user.setRole(Role.ADMIN);
        return toAdminUserResponse(userRepository.save(user));
    }

    @Transactional
    public AdminUserResponse demoteToUser(Long userId) {
        User user = findUserOrThrow(userId);
        // Also blocks a SUPER_ADMIN from demoting themselves - the only way
        // to reach this method as a caller is by being SUPER_ADMIN
        // yourself, and you can't be your own demotion target while also
        // being excluded by this exact check.
        if (user.getRole() == Role.SUPER_ADMIN) {
            throw new BadRequestException("Cannot demote the super admin");
        }
        if (user.getRole() == Role.USER) {
            throw new BadRequestException("This user is not an admin");
        }
        user.setRole(Role.USER);
        return toAdminUserResponse(userRepository.save(user));
    }

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
    }

    private AdminUserResponse toAdminUserResponse(User user) {
        return AdminUserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .emailVerified(user.isEmailVerified())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private UserProfileResponse toResponse(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .authProvider(user.getAuthProvider().name())
                .gender(user.getGender())
                .phoneNumber(user.getPhoneNumber())
                .address(user.getAddress())
                .bio(user.getBio())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
