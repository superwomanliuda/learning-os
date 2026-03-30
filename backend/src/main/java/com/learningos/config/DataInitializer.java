package com.learningos.config;

import com.learningos.model.User;
import com.learningos.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner seedUsers(UserRepository users, PasswordEncoder encoder) {
        return args -> {
            if (users.count() > 0) {
                return;
            }
            User student = new User();
            student.setName("演示学生");
            student.setUsername("student");
            student.setPassword(encoder.encode("demo123"));
            student.setRole(User.Role.STUDENT);
            student.setGrade("初二");
            users.save(student);

            User teacher = new User();
            teacher.setName("演示老师");
            teacher.setUsername("teacher");
            teacher.setPassword(encoder.encode("demo123"));
            teacher.setRole(User.Role.TEACHER);
            users.save(teacher);
        };
    }
}
