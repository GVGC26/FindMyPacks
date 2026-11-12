package com.findmypacks.cli;

import com.findmypacks.logging.StructuredOperationLogger;
import com.findmypacks.logging.StructuredOperationLogger.Level;
import com.findmypacks.user.AppUserDataAccess;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
@ConditionalOnProperty(name = "app.cli.enabled", havingValue = "true")
public class AdminStatusCliRunner implements ApplicationRunner {
    private final AppUserDataAccess users;
    private final StructuredOperationLogger operationLogger;
    private final String adminUsername;
    private final String operation;

    public AdminStatusCliRunner(
            AppUserDataAccess users,
            StructuredOperationLogger operationLogger,
            @Value("${app.admin.username}") String adminUsername,
            @Value("${app.cli.operation:admin-status}") String operation) {
        this.users = users;
        this.operationLogger = operationLogger;
        this.adminUsername = adminUsername;
        this.operation = operation;
    }

    @Override
    public void run(ApplicationArguments args) {
        String requestId = UUID.randomUUID().toString();
        MDC.put("request_id", requestId);
        MDC.put("client", "cli");
        try {
            if (!"admin-status".equals(operation)) {
                throw new IllegalArgumentException("Unsupported CLI operation");
            }
            boolean adminExists = users.findByUsername(adminUsername).isPresent();
            operationLogger.log(Level.INFO, "api", operation, "success",
                    adminExists ? "Administrator exists" : "Administrator is missing");
        } catch (RuntimeException exception) {
            operationLogger.log(Level.ERROR, "api", operation, "error", "CLI operation failed");
            throw exception;
        } finally {
            MDC.remove("request_id");
            MDC.remove("client");
        }
    }
}
