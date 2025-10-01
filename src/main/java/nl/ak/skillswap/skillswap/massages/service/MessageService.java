package nl.ak.skillswap.skillswap.massages.service;

import nl.ak.skillswap.skillswap.massages.domain.Message;

import java.util.List;
import java.util.UUID;

public interface MessageService {
    Message create(UUID userId, String content);
    List<Message> list(int limit);
}
