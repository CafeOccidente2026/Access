package com.cafeoccidente.backend.users.controller;

import com.cafeoccidente.backend.users.dto.UserRequest;
import com.cafeoccidente.backend.users.dto.UserResponse;
import com.cafeoccidente.backend.users.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public UserResponse createUser(@Valid @RequestBody UserRequest request) {
        return userService.createUser(request);
    }

    @GetMapping
    public List<UserResponse> listUsers() {
        return userService.listUsers();
    }

    @PatchMapping("/{id}/deactivate")
    public void deactivateUser(@PathVariable Long id) {
        userService.deactivateUser(id);
    }
}
