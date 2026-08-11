package com.eventbooking.repository;

import com.eventbooking.entity.BookingSeat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingSeatRepository extends JpaRepository<BookingSeat, Long> {
}
