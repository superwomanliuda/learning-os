package com.learningos.controller;

import com.learningos.dto.UserResponse;
import com.learningos.repository.UserRepository;
import com.learningos.util.SecurityUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public UserResponse me() {
        var p = SecurityUtils.currentPrincipal();
        return userRepository.findByUsername(p.getUsername())
                .map(UserResponse::from)
                .orElseThrow(() -> new IllegalStateException("用户不存在"));
    }
}
