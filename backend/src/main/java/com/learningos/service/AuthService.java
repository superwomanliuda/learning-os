package com.learningos.service;

import com.learningos.dto.AuthRequest;
import com.learningos.dto.AuthResponse;
import com.learningos.dto.RegisterRequest;
import com.learningos.model.User;
import com.learningos.repository.UserRepository;
import com.learningos.util.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new IllegalArgumentException("用户名已存在");
        }
        User u = new User();
        u.setName(req.getName());
        u.setUsername(req.getUsername());
        u.setPassword(passwordEncoder.encode(req.getPassword()));
        u.setRole(req.getRole() != null ? req.getRole() : User.Role.STUDENT);
        u.setGrade(req.getGrade());
        userRepository.save(u);
        String token = jwtUtil.generateToken(u.getId(), u.getUsername(), u.getRole().name());
        return new AuthResponse(token, u.getId(), u.getName(), u.getRole().name());
    }

    public AuthResponse login(AuthRequest req) {
        User u = userRepository.findByUsername(req.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));
        if (!passwordEncoder.matches(req.getPassword(), u.getPassword())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        String token = jwtUtil.generateToken(u.getId(), u.getUsername(), u.getRole().name());
        return new AuthResponse(token, u.getId(), u.getName(), u.getRole().name());
    }
}
