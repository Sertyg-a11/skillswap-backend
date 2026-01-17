package nl.ak.skillswap.messageservice.service;

import lombok.RequiredArgsConstructor;
import nl.ak.skillswap.messageservice.domain.Conversation;
import nl.ak.skillswap.messageservice.repository.ConversationRepository;
import nl.ak.skillswap.messageservice.support.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;

    @Transactional
    public Conversation getOrCreate(UUID me, UUID other) {
        UUID low = low(me, other);
        UUID high = high(me, other);

        return conversationRepository.findByUserLowIdAndUserHighId(low, high)
                .orElseGet(() -> conversationRepository.save(
                        Conversation.builder()
                                .id(UUID.randomUUID())
                                .userLowId(low)
                                .userHighId(high)
                                .createdAt(Instant.now())
                                .lastMessageAt(null)
                                .build()
                ));
    }

    @Transactional(readOnly = true)
    public Conversation getOrThrow(UUID conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation not found"));
    }

    @Transactional(readOnly = true)
    public List<Conversation> listForUser(UUID userId) {
        return conversationRepository
                .findByUserLowIdOrUserHighIdOrderByLastMessageAtDescCreatedAtDesc(userId, userId);
    }

    @Transactional
    public void touchLastMessage(Conversation conversation, Instant at) {
        conversation.setLastMessageAt(at);
        conversationRepository.save(conversation);
    }

    private UUID low(UUID a, UUID b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    private UUID high(UUID a, UUID b) {
        return a.compareTo(b) <= 0 ? b : a;
    }
}
