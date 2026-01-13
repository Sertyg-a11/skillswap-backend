package nl.ak.skillswap.userservice.repository;

import nl.ak.skillswap.userservice.domain.PrivacyEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PrivacyEventRepository extends JpaRepository<PrivacyEvent, Long> {
    List<PrivacyEvent> findByUserIdOrderByCreatedAtDesc(UUID userId);
}