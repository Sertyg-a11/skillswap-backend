package nl.ak.skillswap.messageservice.repository;


import nl.ak.skillswap.messageservice.domain.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByUserLowIdAndUserHighId(UUID userLowId, UUID userHighId);

    List<Conversation> findByUserLowIdOrUserHighIdOrderByLastMessageAtDescCreatedAtDesc(
            UUID userLowId,
            UUID userHighId
    );
}
