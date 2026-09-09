package com.zkuba.trading.config;

import org.springframework.context.annotation.Configuration;
import io.github.cdimascio.dotenv.Dotenv;

@Configuration
public class DotenvConfig {
    static {
        Dotenv dotenv = Dotenv.configure()
            .directory("sprint-06-trade-api")
            .ignoreIfMissing()
            .load();
        
        // Set database and app properties from .env
        setPropertyIfPresent(dotenv, "SERVER_PORT");
        setPropertyIfPresent(dotenv, "DATABASE_URL");
        setPropertyIfPresent(dotenv, "DATABASE_USERNAME");
        setPropertyIfPresent(dotenv, "DATABASE_PASSWORD");
        setPropertyIfPresent(dotenv, "JWT_SECRET");
        setPropertyIfPresent(dotenv, "JWT_EXPIRATION_MS");
        setPropertyIfPresent(dotenv, "DEFAULT_INITIAL_BALANCE");
        setPropertyIfPresent(dotenv, "LOG_LEVEL");
        setPropertyIfPresent(dotenv, "APP_LOG_LEVEL");
    }
    
    private static void setPropertyIfPresent(Dotenv dotenv, String key) {
        String value = dotenv.get(key);
        if (value != null && !value.isEmpty()) {
            System.setProperty(key, value);
        }
    }
}