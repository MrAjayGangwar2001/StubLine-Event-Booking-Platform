package com.eventbooking.controller;

import com.eventbooking.dto.auth.AdminUserResponse;
import com.eventbooking.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Deliberately requires SUPER_ADMIN, not just ADMIN, on every endpoint here -
 * this is the entire fix for the "Admin promotes user1, user1 promotes
 * user2, ..." chaining problem. A regular ADMIN has full access to manage
 * events/bookings/analytics/announcements, but has NO path to create more
 * admins - only the single seeded SUPER_ADMIN account (or another
 * SUPER_ADMIN promoted the same way, if ever needed) can do that.
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping
    public ResponseEntity<List<AdminUserResponse>> searchUsers(@RequestParam(required = false) String search) {
        return ResponseEntity.ok(userService.searchUsers(search));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/{id}/promote")
    public ResponseEntity<AdminUserResponse> promoteToAdmin(@PathVariable Long id) {
        return ResponseEntity.ok(userService.promoteToAdmin(id));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/{id}/demote")
    public ResponseEntity<AdminUserResponse> demoteToUser(@PathVariable Long id) {
        return ResponseEntity.ok(userService.demoteToUser(id));
    }
}
