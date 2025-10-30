package org.example.eye_service.listeners;

import events.UserCreateEvent;
import events.UserDeleteEvent;
import org.slf4j.Logger;
import com.github.javafaker.Faker;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import java.io.*;
import java.util.Locale;

@Component
public class UserEventListener {
    private static final Logger log = LoggerFactory.getLogger(UserEventListener.class);

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "user-create-notification-queue", durable = "true"),
            exchange = @Exchange(name = "auth-exchange", type = "topic"),
            key = "user.created"
    ))
    public void handleUserCreatedEvent(UserCreateEvent event) {
        String userFIO = getUserFio(event.getEmail());
        writeUserEmailToFile(event.getEmail(), userFIO);

        log.info("New user signed up with email: {}, found FIO: {}", event.getEmail(), userFIO);
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "user-delete-notification-queue", durable = "true"),
            exchange = @Exchange(name = "auth-exchange", type = "topic"),
            key = "user.deleted"
    ))
    public void handleUserDeletedEvent(UserDeleteEvent event) {
        log.info("User delete account with email: {}", event.getEmail());
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