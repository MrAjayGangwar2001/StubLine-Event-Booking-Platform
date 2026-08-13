package com.eventbooking.dto.event;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * Describes how to auto-generate the physical seat layout for a venue.
 * e.g. rows A-J, 20 seats each, front 3 rows PLATINUM, next 4 GOLD, rest SILVER.
 */
@Data
public class SeatGenerationRequest {

    @NotEmpty(message = "At least one row config is required")
    private List<RowConfig> rows;

    @Data
    public static class RowConfig {
        @NotNull
        private String rowLabel;      // e.g. "A"

        @NotNull
        private Integer seatCount;    // e.g. 20

        @NotNull
        private String tier;          // GOLD / SILVER / PLATINUM
    }
}
