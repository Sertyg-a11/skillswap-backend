package nl.ak.skillswap.userservice.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import nl.ak.skillswap.userservice.api.dto.SkillDto;
import nl.ak.skillswap.userservice.domain.User;
import nl.ak.skillswap.userservice.repository.SkillRepository;
import nl.ak.skillswap.userservice.repository.UserRepository;
import nl.ak.skillswap.userservice.service.InputSanitizer;
import nl.ak.skillswap.userservice.service.SkillService;
import nl.ak.skillswap.userservice.service.UserService;
import nl.ak.skillswap.userservice.support.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/skills")
public class SkillController {

    private final SkillService skillService;
    private final UserService userService;
    private final UserRepository userRepository;
    private final SkillRepository skillRepository;
    private final InputSanitizer inputSanitizer;

    @GetMapping("/me")
    public List<SkillDto> mySkills(Authentication authentication) {
        User me = sync(authentication);
        return skillService.listForUser(me.getId()).stream().map(SkillDto::from).toList();
    }

    /**
     * Get skills for a specific user (for viewing other users' profiles).
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<SkillDto>> getUserSkills(@PathVariable UUID userId, Authentication authentication) {
        // Ensure requesting user is authenticated
        sync(authentication);

        // Only return skills if user exists, is active, and allows matching
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .filter(User::isActive)
                .filter(User::isAllowMatching)
                .map(user -> ResponseEntity.ok(
                        skillRepository.findByUserId(userId).stream()
                                .map(SkillDto::from)
                                .toList()
                ))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/me")
    public SkillDto add(Authentication authentication, @Valid @RequestBody CreateSkillRequest req) {
        User me = sync(authentication);

        // OWASP: Sanitize input to prevent XSS
        String sanitizedName = inputSanitizer.sanitizeSkillName(req.name());
        String sanitizedLevel = req.level() != null ? inputSanitizer.sanitizeText(req.level()) : null;
        String sanitizedCategory = req.category() != null ? inputSanitizer.sanitizeText(req.category()) : null;
        String sanitizedDescription = req.description() != null ? inputSanitizer.sanitizeText(req.description()) : null;

        var created = skillService.addSkill(me.getId(), new SkillService.CreateSkillCommand(
                sanitizedName, sanitizedLevel, sanitizedCategory, sanitizedDescription
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
