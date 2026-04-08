package be.ucll.controller;

import be.ucll.dto.UserProfileRequest;
import be.ucll.dto.UserProfileResponse;
import be.ucll.model.User;
import be.ucll.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/profile")
public class UserProfileController {

    private final UserRepository userRepository;

    public UserProfileController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // GET /api/profile - Get current user profile
    @GetMapping
    public ResponseEntity<UserProfileResponse> getProfile(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }

        String email = authentication.getName();
        
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return ResponseEntity.ok(new UserProfileResponse(
            user.getUserId(),
            user.getName(),
            user.getEmail(),
            user.getRole().name(),
            user.getAvatarEmoji() != null ? user.getAvatarEmoji() : "🦙",
            user.getAvatarColor() != null ? user.getAvatarColor() : "#f97316",
            user.isDarkMode(),
            user.isNotificationsEnabled()  // ✅ Added
        ));
    }

    @PutMapping
    public ResponseEntity<UserProfileResponse> updateProfile(
            Authentication authentication,
            @RequestBody UserProfileRequest request
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }

        String email = authentication.getName();

        User dbUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (request.name() != null && !request.name().isBlank()) {
            dbUser.setName(request.name().trim());
        }
        if (request.avatarEmoji() != null) {
            dbUser.setAvatarEmoji(request.avatarEmoji());
        }
        if (request.avatarColor() != null) {
            dbUser.setAvatarColor(request.avatarColor());
        }
        dbUser.setDarkMode(request.darkMode());
        
        // ✅ Update notifications preference (only for admins)
        if (request.notificationsEnabled() != null && dbUser.getRole() == User.Role.ADMIN) {
            dbUser.setNotificationsEnabled(request.notificationsEnabled());
        }

        userRepository.save(dbUser);

        return ResponseEntity.ok(new UserProfileResponse(
            dbUser.getUserId(),
            dbUser.getName(),
            dbUser.getEmail(),
            dbUser.getRole().name(),
            dbUser.getAvatarEmoji(),
            dbUser.getAvatarColor(),
            dbUser.isDarkMode(),
            dbUser.isNotificationsEnabled()  // ✅ Added
        ));
    }
}