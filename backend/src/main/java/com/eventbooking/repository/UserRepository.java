package com.eventbooking.repository;

import com.eventbooking.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u WHERE LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "OR LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%'))")
    List<User> searchByEmailOrName(@Param("search") String search);
}
