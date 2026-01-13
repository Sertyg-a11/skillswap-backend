package nl.ak.skillswap.userservice.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.messaging.exchange}")
    private String exchange;

    @Value("${app.messaging.routingKeyUserDeleted}")
    private String routingKeyUserDeleted;

    public void publishUserDeleted(UserDeletedEvent event) {
        rabbitTemplate.convertAndSend(exchange, routingKeyUserDeleted, event);
    }
}