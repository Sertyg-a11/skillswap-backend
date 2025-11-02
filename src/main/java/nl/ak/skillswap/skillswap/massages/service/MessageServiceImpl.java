package nl.ak.skillswap.skillswap.massages.service;

import nl.ak.skillswap.skillswap.massages.domain.Message;
import nl.ak.skillswap.skillswap.massages.repo.MessageRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class MessageServiceImpl implements MessageService {

    private final MessageRepository repo;

    public MessageServiceImpl(MessageRepository repo) {
        this.repo = repo;
    }

    @Override
    public Message create(UUID userId, String content) {
        var m = new Message(userId, content, OffsetDateTime.now());
        return repo.save(m); // JPA generates the ID
    }

    @Override
    public List<Message> list(int limit) {
        return repo.findAllByOrderByCreatedAtDesc(PageRequest.of(0, Math.min(limit, 200)));
    }
}
