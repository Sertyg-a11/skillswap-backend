package nl.ak.skillswap.skillswap.massages.api;

import nl.ak.skillswap.skillswap.massages.api.dto.MessageDto;
import nl.ak.skillswap.skillswap.massages.domain.Message;
import nl.ak.skillswap.skillswap.massages.service.MessageService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/messages")
@Validated
public class MessageController {

    private final MessageService service;
    public MessageController(MessageService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<Message> create(@RequestBody @jakarta.validation.Valid MessageDto dto) {
        var saved = service.create(dto.userId(), dto.content());
        return ResponseEntity.ok(saved);
    }

    @GetMapping
    public List<Message> list(@RequestParam(defaultValue = "50") int limit) {
        return service.list(limit);
    }
}
