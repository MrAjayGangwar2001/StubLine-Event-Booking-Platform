package com.eventbooking.dto.announcement;

import com.eventbooking.entity.AnnouncementSeverity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementResponse {
    private Long id;
    private String message;
    private AnnouncementSeverity severity;
    private Boolean active;
    private LocalDateTime createdAt;
}
