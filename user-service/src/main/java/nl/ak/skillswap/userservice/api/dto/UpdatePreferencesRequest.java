package nl.ak.skillswap.userservice.api.dto;

public record UpdatePreferencesRequest(
        boolean allowMatching,
        boolean allowEmails
) {}
