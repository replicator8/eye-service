package org.example.eye_service.config;

import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;

@Configuration
public class RabbitMQConfig {

    @Bean
    public SimpleMessageConverter messageConverter() {
        SimpleMessageConverter converter = new SimpleMessageConverter();
        converter.setAllowedListPatterns(List.of(
                "events.UserCreateEvent",
                "events.*",
                "com.example.demo.events.*",
                "com.example.*",
                "com.example.*"
        ));
        return converter;
    }
}
