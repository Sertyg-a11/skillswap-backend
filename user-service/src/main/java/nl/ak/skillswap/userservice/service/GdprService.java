package nl.ak.skillswap.userservice.service;

import lombok.RequiredArgsConstructor;
import nl.ak.skillswap.userservice.domain.PrivacyEvent;
import nl.ak.skillswap.userservice.domain.PrivacyEventType;
import nl.ak.skillswap.userservice.domain.Skill;
import nl.ak.skillswap.userservice.domain.User;
import nl.ak.skillswap.userservice.repository.PrivacyEventRepository;
import nl.ak.skillswap.userservice.repository.SkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GdprService {

    private final UserService userService;
    private final SkillRepository skills;
    private final PrivacyEventRepository privacyEvents;

    @Transactional
    public ExportBundle exportAll(UUID userId) {
        User u = userService.getActiveOrThrow(userId);

        List<Skill> userSkills = skills.findByUserId(userId);
        List<PrivacyEvent> events = privacyEvents.findByUserIdOrderByCreatedAtDesc(userId);

        // audit export
        privacyEvents.save(PrivacyEvent.of(userId, PrivacyEventType.DATA_EXPORTED, null));

        return new ExportBundle(u, userSkills, events);
    }

    public record ExportBundle(User user, List<Skill> skills, List<PrivacyEvent> privacyEvents) {}
}
