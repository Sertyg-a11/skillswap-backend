package nl.ak.skillswap.messageservice.api;

import lombok.RequiredArgsConstructor;
import nl.ak.skillswap.messageservice.api.dto.ConversationDto;
import nl.ak.skillswap.messageservice.domain.Conversation;
import nl.ak.skillswap.messageservice.service.ConversationService;
import nl.ak.skillswap.messageservice.service.MessageService;
import nl.ak.skillswap.messageservice.support.AuthenticatedUserContext;
import nl.ak.skillswap.messageservice.support.UserContextResolver;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final MessageService messageService;
    private final UserContextResolver userContextResolver;

    @GetMapping
    public List<ConversationDto> myConversations(Authentication authentication) {
        AuthenticatedUserContext ctx = userContextResolver.resolve(authentication);
        UUID me = ctx.databaseId();
        List<Conversation> conversations = conversationService.listForUser(me);

        return conversations.stream()
                .map(c -> new ConversationDto(
                        c.getId(),
                        c.otherParticipant(me),
                        c.getCreatedAt(),
                        c.getLastMessageAt(),
                        messageService.unreadCount(me, c.getId())
                ))
                .toList();
    }
}
