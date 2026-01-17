package nl.ak.skillswap.userservice.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank @Size(max = 100) String displayName,
        @Size(max = 64) String timeZone,
        @Size(max = 2000) String bio
) {}
