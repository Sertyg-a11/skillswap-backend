package nl.ak.skillswap.userservice.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import nl.ak.skillswap.userservice.api.dto.SkillDto;
import nl.ak.skillswap.userservice.domain.User;
import nl.ak.skillswap.userservice.service.SkillService;
import nl.ak.skillswap.userservice.service.UserService;
import nl.ak.skillswap.userservice.support.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/skills")
public class SkillController {

    private final SkillService skillService;
    private final UserService userService;

    @GetMapping("/me")
    public List<SkillDto> mySkills(Authentication authentication) {
        User me = sync(authentication);
        return skillService.listForUser(me.getId()).stream().map(SkillDto::from).toList();
    }

    @PostMapping("/me")
    public SkillDto add(Authentication authentication, @Valid @RequestBody CreateSkillRequest req) {
        User me = sync(authentication);
        var created = skillService.addSkill(me.getId(), new SkillService.CreateSkillCommand(
                req.name(), req.level(), req.category(), req.description()
        ));
        return SkillDto.from(created);
    }

    private User sync(Authentication authentication) {
        return userService.syncFromKeycloak(
                CurrentUser.externalId(authentication),
                CurrentUser.email(authentication),
                CurrentUser.preferredUsername(authentication)
        );
    }

    public record CreateSkillRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 32) String level,
            @Size(max = 64) String category,
            @Size(max = 2000) String description
    ) {}
}
