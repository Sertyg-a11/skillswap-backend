package nl.ak.skillswap.userservice.service;

import lombok.RequiredArgsConstructor;
import nl.ak.skillswap.userservice.domain.Skill;
import nl.ak.skillswap.userservice.repository.SkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SkillService {

    private final SkillRepository skills;

    @Transactional(readOnly = true)
    public List<Skill> listForUser(UUID userId) {
        return skills.findByUserId(userId);
    }

    @Transactional
    public Skill addSkill(UUID userId, CreateSkillCommand cmd) {
        Skill skill = Skill.builder()
                .userId(userId)
                .name(cmd.name())
                .level(cmd.level())
                .category(cmd.category())
                .description(cmd.description())
                .build();
        return skills.save(skill);
    }

    public record CreateSkillCommand(String name, String level, String category, String description) { }
}
