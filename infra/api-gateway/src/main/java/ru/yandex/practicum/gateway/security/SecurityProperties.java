package ru.yandex.practicum.gateway.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties("app.security")
public class SecurityProperties {

    private List<User> users;

    @Getter
    @Setter
    public static class User {
        private String username;
        private String password;
        private List<String> roles;
    }
}