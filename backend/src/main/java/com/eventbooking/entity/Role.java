package com.eventbooking.entity;

public enum Role {
    USER,
    ADMIN,
     // Deliberately separate from ADMIN, not just "the first admin" by
    // convention - only SUPER_ADMIN can promote/demote other users
    // (AdminUserController), so a regular ADMIN has no way to create more
    // admins, which is what stops Admin -> promotes user1 -> user1 promotes
    // user2 -> ... from ever being possible. See User.getAuthorities() for
    // how a SUPER_ADMIN still gets full ADMIN access everywhere else.
    SUPER_ADMIN
}
