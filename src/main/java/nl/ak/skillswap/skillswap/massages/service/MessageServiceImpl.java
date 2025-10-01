package nl.ak.skillswap.skillswap.massages.service;

import nl.ak.skillswap.skillswap.massages.domain.Message;
import nl.ak.skillswap.skillswap.massages.repo.MessageRepository;
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
        return repo.save(new Message(0L, userId, content, OffsetDateTime.now()));
    }

    @Override
    public List<Message> list(int limit) {
        return repo.list(Math.min(limit, 200));
    }
}
