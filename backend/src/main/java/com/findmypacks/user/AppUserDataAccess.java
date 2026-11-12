package com.findmypacks.user;

import com.findmypacks.logging.StructuredOperationLogger;
import com.findmypacks.logging.StructuredOperationLogger.Level;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class AppUserDataAccess {
    private final AppUserRepository users;
    private final StructuredOperationLogger operationLogger;

    public AppUserDataAccess(AppUserRepository users, StructuredOperationLogger operationLogger) {
        this.users = users;
        this.operationLogger = operationLogger;
    }

    public Optional<AppUser> findByUsername(String username) {
        try {
            Optional<AppUser> result = users.findByUsername(username);
            operationLogger.log(Level.INFO, "db", "find_user_by_username", "success", "User record read");
            return result;
        } catch (RuntimeException exception) {
            operationLogger.log(Level.ERROR, "db", "find_user_by_username", "error", "User record read failed");
            throw exception;
        }
    }
}
