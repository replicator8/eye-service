package org.example.eye_service.listeners;

import events.UserCreateEvent;
import events.UserDeleteEvent;
import com.github.javafaker.Faker;
import java.io.*;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class UserEventListener {
    private static final Logger log = LoggerFactory.getLogger(UserEventListener.class);
    private static final String EXCHANGE_NAME = "auth-exchange";
    private static final String QUEUE_NAME = "user-notification-queue";
    private static final String QUEUE_NAME_CREATED = "user-notification-create-queue";
    private static final String QUEUE_NAME_DELETED = "user-notification-deleted-queue";
    private final Set<Long> processedUserCreations = ConcurrentHashMap.newKeySet();

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = QUEUE_NAME, durable = "true",
                    // если что-то пойдет не так, отправляем в 'dlx-exchange'
                    arguments = {
                            @Argument(name = "x-dead-letter-exchange", value = "dlx-exchange"),
                            @Argument(name = "x-dead-letter-routing-key", value = "dlq.notifications")}),
            exchange = @Exchange(name = EXCHANGE_NAME, type = "topic", durable = "true"),
            key = "user.created"))
    public void handleUserCreatedEvent(@Payload UserCreateEvent event, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        if (!processedUserCreations.add(event.getId())) {
            log.warn("Duplicate event received for userID: {}", event.getId());
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            String userFIO = getUserFio(event.getEmail());
            writeUserEmailToFile(event.getEmail(), userFIO);
            log.info("New user signed up with email: {}, found FIO: {}", event.getEmail(), userFIO);

            if (event.getEmail() != null && event.getEmail().contains("crash")) {
                throw new RuntimeException("Simulating processing error for DLQ test");
            }

            log.info("Notification sent for new user '{}'!", event.getEmail());
            // Отправляем подтверждение брокеру
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("Failed to process event: {}. Sending to DLQ.", event, e);
            // Отправляем nack и НЕ просим вернуть в очередь (requeue=false)
            channel.basicNack(deliveryTag, false, false);
        }
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = QUEUE_NAME, durable = "true", arguments = {
                            @Argument(name = "x-dead-letter-exchange", value = "dlx-exchange"),
                            @Argument(name = "x-dead-letter-routing-key", value = "dlq.notifications")}),
            exchange = @Exchange(name = EXCHANGE_NAME, type = "topic", durable = "true"),
            key = "user.deleted"))
    public void handleUserDeletedEvent(@Payload UserDeleteEvent event, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            log.info("User delete account with email: {}", event.getEmail());
            log.info("Notifications cancelled for deleted userId {}!", event.getId());

            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("Failed to process event: {}. Sending to DLQ.", event, e);

            channel.basicNack(deliveryTag, false, false);
        }
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "user-notification-queue.dlq", durable = "true"),
            exchange = @Exchange(name = "dlx-exchange", type = "topic", durable = "true"),
            key = "dlq.notifications"))
    public void handleDlqMessages(Object failedMessage) {
        log.error("!!! Received message in DLQ: {}", failedMessage);
        // Здесь может быть логика оповещения администраторов
    }

    private String getUserFio(String email) {
        Faker faker = new Faker(new Locale("ru"));
        return faker.name().fullName();
    }

    private void writeUserEmailToFile(String email, String fio) {
        String fileName = "users.txt";
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName, true))) {
            writer.write(email + " - " + fio);
            writer.newLine();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}