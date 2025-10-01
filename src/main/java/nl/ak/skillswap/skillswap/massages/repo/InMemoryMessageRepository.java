package nl.ak.skillswap.skillswap.massages.repo;

import nl.ak.skillswap.skillswap.massages.domain.Message;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class InMemoryMessageRepository implements MessageRepository {
    private final ConcurrentHashMap<Long, Message> store = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong(0);

    @Override
    public Message save(Message m) {
        long id = seq.incrementAndGet();
        Message withId = new Message(id, m.getUserId(), m.getContent(), OffsetDateTime.now());
        store.put(id, withId);
        return withId;
    }

    @Override
    public List<Message> list(int limit) {
        var copy = new ArrayList<>(store.values());
        copy.sort((a,b) -> a.getCreatedAt().compareTo(b.getCreatedAt()));
        Collections.reverse(copy); // newest first
        return copy.stream().limit(limit).toList();
    }
}
