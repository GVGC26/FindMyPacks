package com.findmypacks.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminUserSeeder implements ApplicationRunner {
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public AdminUserSeeder(
            AppUserRepository users,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.username}") String username,
            @Value("${app.admin.password}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.findByUsername(username).isEmpty()) {
            users.save(new AppUser(username, passwordEncoder.encode(password), "ADMIN"));
        }
    }
}
