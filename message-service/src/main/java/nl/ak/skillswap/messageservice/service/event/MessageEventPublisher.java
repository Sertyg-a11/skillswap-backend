package nl.ak.skillswap.messageservice.service.event;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MessageEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.messaging.exchange}")
    private String exchange;

    @Value("${app.messaging.routingKeyMessageCreated}")
    private String routingKey;

    public void publishMessageCreated(MessageCreatedEvent event) {
        rabbitTemplate.convertAndSend(exchange, routingKey, event);
    }
}
