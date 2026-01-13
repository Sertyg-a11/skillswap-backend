package nl.ak.skillswap.userservice.repository;

import nl.ak.skillswap.userservice.domain.Skill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SkillRepository extends JpaRepository<Skill, UUID> {
    List<Skill> findByUserId(UUID userId);
    List<Skill> findByNameIgnoreCase(String name);
}
