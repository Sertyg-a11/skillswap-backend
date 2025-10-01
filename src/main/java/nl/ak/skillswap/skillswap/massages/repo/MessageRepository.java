package nl.ak.skillswap.skillswap.massages.repo;

import nl.ak.skillswap.skillswap.massages.domain.Message;

import java.util.List;

public interface MessageRepository {
    Message save(Message message);
    List<Message> list(int limit);
}
