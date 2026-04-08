package be.ucll.controller;

import java.util.Map;
import java.util.Optional;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import be.ucll.model.User;
import be.ucll.service.UserService;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // 🧍 Register new user
    @PostMapping("/register")
    public User register(@RequestBody User user) {
        return userService.register(user);
    }

    // 🔑 Login existing user
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String password = body.get("password");

        Optional<User> userOpt = userService.login(email, password);
        if (userOpt.isEmpty()) {
            return Map.of("success", false, "message", "Invalid credentials");
        }

        User user = userOpt.get();

        // (Optional) You could generate a JWT here if you want real token-based auth.
        return Map.of(
            "success", true,
            "userId", user.getUserId(),
            "name", user.getName(),
            "email", user.getEmail(),
            "role", user.getRole()
        );
    }
}
