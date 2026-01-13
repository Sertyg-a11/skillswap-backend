package nl.ak.skillswap.messageservice.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nl.ak.skillswap.messageservice.api.dto.MessageDto;
import nl.ak.skillswap.messageservice.api.dto.PageResponse;
import nl.ak.skillswap.messageservice.api.dto.SendMessageRequest;
import nl.ak.skillswap.messageservice.domain.Message;
import nl.ak.skillswap.messageservice.service.MessageService;
import nl.ak.skillswap.messageservice.support.CurrentUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;

    // Send message to another user (creates conversation if missing)
    @PostMapping("/to/{otherUserId}")
    public MessageDto send(
            Authentication authentication,
            @PathVariable UUID otherUserId,
            @Valid @RequestBody SendMessageRequest request
    ) {
        UUID me = CurrentUser.userId(authentication);
        Message m = messageService.sendMessage(me, otherUserId, request.body());
        return toDto(m);
    }

    // List messages in a conversation (cursor pagination by createdAt)
    @GetMapping("/conversation/{conversationId}")
    public PageResponse<MessageDto> list(
            Authentication authentication,
            @PathVariable UUID conversationId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant before,
            @RequestParam(defaultValue = "50") int size
    ) {
        UUID me = CurrentUser.userId(authentication);
        List<Message> items = messageService.listMessages(me, conversationId, before, size);
        boolean hasMore = items.size() == Math.min(Math.max(size, 1), 100);

        return new PageResponse<>(
                items.stream().map(this::toDto).toList(),
                hasMore
        );
    }

    // Mark entire conversation as read for current user
    @PostMapping("/conversation/{conversationId}/read")
    public int markRead(Authentication authentication, @PathVariable UUID conversationId) {
        UUID me = CurrentUser.userId(authentication);
        return messageService.markRead(me, conversationId);
    }

    private MessageDto toDto(Message m) {
        return new MessageDto(
                m.getId(),
                m.getConversationId(),
                m.getSenderId(),
                m.getRecipientId(),
                m.getBody(),
                m.getCreatedAt(),
                m.getReadAt()
        );
    }
}

