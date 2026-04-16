package com.learningos;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.List;

@SpringBootApplication
public class LearningOsApplication {
    public static void main(String[] args) {
        loadDotEnvIntoSystemProperties();
        SpringApplication.run(LearningOsApplication.class, args);
    }

    /**
     * Allow backend to read root .env automatically:
     * - when running in backend/: reads ../.env
     * - when running in repo root: reads ./.env
     * Existing OS env vars keep highest priority.
     */
    private static void loadDotEnvIntoSystemProperties() {
        List<String> directories = List.of(".", "..");
        for (String dir : directories) {
            Dotenv dotenv = Dotenv.configure()
                    .directory(dir)
                    .ignoreIfMissing()
                    .ignoreIfMalformed()
                    .load();
            dotenv.entries().forEach(entry -> {
                String key = entry.getKey();
                if (System.getenv(key) == null && System.getProperty(key) == null) {
                    System.setProperty(key, entry.getValue());
                }
            });
        }
    }
}
