package nl.ak.skillswap.userservice.api.dto;

import java.util.List;

public record GdprExportResponse(
        UserDto user,
        List<SkillDto> skills,
        List<PrivacyEventDto> privacyEvents
) {}

