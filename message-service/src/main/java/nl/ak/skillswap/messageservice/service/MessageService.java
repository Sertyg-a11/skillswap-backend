package nl.ak.skillswap.messageservice.service;

import lombok.RequiredArgsConstructor;
import nl.ak.skillswap.messageservice.domain.Conversation;
import nl.ak.skillswap.messageservice.domain.Message;
import nl.ak.skillswap.messageservice.repository.MessageRepository;
import nl.ak.skillswap.messageservice.service.event.MessageCreatedEvent;
import nl.ak.skillswap.messageservice.service.event.MessageEventPublisher;
import nl.ak.skillswap.messageservice.support.ForbiddenException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final ConversationService conversationService;
    private final MessageRepository messageRepository;
    private final UnreadCounterService unreadCounterService;
    private final MessageEventPublisher eventPublisher;

    @Transactional
    public Message sendMessage(UUID me, UUID otherUserId, String body) {
        Conversation conversation = conversationService.getOrCreate(me, otherUserId);

        if (!conversation.involves(me)) {
            throw new ForbiddenException("Not allowed");
        }

        Instant now = Instant.now();
        Message message = messageRepository.save(
                Message.builder()
                        .id(UUID.randomUUID())
                        .conversationId(conversation.getId())
                        .senderId(me)
                        .recipientId(otherUserId)
                        .body(body)
                        .createdAt(now)
                        .readAt(null)
                        .build()
        );

        conversationService.touchLastMessage(conversation, now);

        unreadCounterService.incrementUnread(otherUserId, conversation.getId());

        eventPublisher.publishMessageCreated(new MessageCreatedEvent(
                message.getId(),
                message.getConversationId(),
                message.getSenderId(),
                message.getRecipientId(),
                message.getCreatedAt()
        ));

        return message;
    }

    @Transactional(readOnly = true)
    public List<Message> listMessages(UUID me, UUID conversationId, Instant before, int size) {
        Conversation conversation = conversationService.getOrThrow(conversationId);
        if (!conversation.involves(me)) throw new ForbiddenException("Not allowed");

        int pageSize = Math.min(Math.max(size, 1), 100);
        var pageable = PageRequest.of(0, pageSize);

        if (before == null) {
            return messageRepository.findByConversationIdOrderByCreatedAtDesc(conversationId, pageable);
        }
        return messageRepository.findByConversationIdAndCreatedAtBeforeOrderByCreatedAtDesc(
                conversationId, before, pageable
        );
    }

    @Transactional
    public int markRead(UUID me, UUID conversationId) {
        Conversation conversation = conversationService.getOrThrow(conversationId);
        if (!conversation.involves(me)) throw new ForbiddenException("Not allowed");

        int updated = messageRepository.markConversationRead(conversationId, me, Instant.now());
        unreadCounterService.clearUnread(me, conversationId);
        return updated;
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID me, UUID conversationId) {
        return unreadCounterService.getUnread(me, conversationId);
    }
}
