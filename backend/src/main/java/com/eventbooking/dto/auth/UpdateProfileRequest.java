package com.eventbooking.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100)
    private String name;

    @Size(max = 20, message = "Gender must be 20 characters or fewer")
    private String gender;

    @Size(max = 20, message = "Phone number must be 20 characters or fewer")
    private String phoneNumber;

    @Size(max = 255, message = "Address must be 255 characters or fewer")
    private String address;

    @Size(max = 1000, message = "Bio must be 1000 characters or fewer")
    private String bio;
}
