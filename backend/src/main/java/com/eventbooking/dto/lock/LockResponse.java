package com.eventbooking.dto.lock;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LockResponse {
    private boolean locked;
    private Integer ttlSeconds;
    private String message;
}
