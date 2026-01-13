package nl.ak.skillswap.userservice.api;

import lombok.RequiredArgsConstructor;
import nl.ak.skillswap.userservice.api.dto.GdprExportResponse;
import nl.ak.skillswap.userservice.api.dto.PrivacyEventDto;
import nl.ak.skillswap.userservice.api.dto.SkillDto;
import nl.ak.skillswap.userservice.api.dto.UserDto;
import nl.ak.skillswap.userservice.domain.User;
import nl.ak.skillswap.userservice.service.GdprService;
import nl.ak.skillswap.userservice.service.UserService;
import nl.ak.skillswap.userservice.support.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/gdpr")
public class GdprController {

    private final GdprService gdprService;
    private final UserService userService;

    @GetMapping("/export")
    public GdprExportResponse export(Authentication authentication) {
        User me = userService.syncFromKeycloak(
                CurrentUser.externalId(authentication),
                CurrentUser.email(authentication),
                CurrentUser.preferredUsername(authentication)
        );

        var bundle = gdprService.exportAll(me.getId());

        return new GdprExportResponse(
                UserDto.from(bundle.user()),
                bundle.skills().stream().map(SkillDto::from).toList(),
                bundle.privacyEvents().stream().map(PrivacyEventDto::from).toList()
        );
    }
}
