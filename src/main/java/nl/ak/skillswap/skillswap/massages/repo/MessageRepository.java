package nl.ak.skillswap.skillswap.massages.repo;

import nl.ak.skillswap.skillswap.massages.domain.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;


import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
