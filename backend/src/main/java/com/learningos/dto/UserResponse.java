package com.learningos.dto;

import com.learningos.model.User;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String name;
    private String username;
    private User.Role role;
    private String grade;

    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getName(), u.getUsername(), u.getRole(), u.getGrade());
    }
}
