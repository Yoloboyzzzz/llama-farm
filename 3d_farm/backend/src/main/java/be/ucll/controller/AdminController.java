package be.ucll.controller;

import be.ucll.repository.UserRepository;
import be.ucll.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ============================================================
    // GET /api/admin/users - List all users
    // ============================================================

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        List<User> users = userRepository.findAll();
        
        List<UserDTO> dtos = users.stream()
                .map(u -> new UserDTO(
                        u.getUserId(),
                        u.getName(),
                        u.getEmail(),
                        u.getRole().name(),
                        u.getCanEstimate()
                ))
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }

    // ============================================================
    // POST /api/admin/users - Create new user
    // ============================================================

    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createUser(@RequestBody CreateUserRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Email already exists"));
        }

        User user = new User();
        user.setName(request.name());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(User.Role.valueOf(request.role()));
        user.setCanEstimate(true);

        userRepository.save(user);
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "User created successfully"));
    }

    // ============================================================
    // PUT /api/admin/users/{id} - Update user details
    // ============================================================

    @PutMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateUser(
            @PathVariable Long id,
            @RequestBody UpdateUserRequest request
    ) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setName(request.name());
        user.setEmail(request.email());
        user.setRole(User.Role.valueOf(request.role()));

        userRepository.save(user);
        
        return ResponseEntity.ok(Map.of("message", "User updated successfully"));
    }

    // ============================================================
    // DELETE /api/admin/users/{id} - Delete user
    // ============================================================

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Prevent deleting the last admin
        if (user.getRole() == User.Role.ADMIN) {
            long adminCount = userRepository.findAll().stream()
                    .filter(u -> u.getRole() == User.Role.ADMIN)
                    .count();
            
            if (adminCount <= 1) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Cannot delete the last admin user"));
            }
        }

        userRepository.delete(user);
        
        return ResponseEntity.ok(Map.of("message", "User deleted successfully"));
    }

    // ============================================================
    // PUT /api/admin/users/{id}/estimate-access - Toggle estimate access
    // ============================================================

    @PutMapping("/users/{id}/estimate-access")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> toggleEstimateAccess(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body
    ) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Boolean canEstimate = body.get("canEstimate");
        if (canEstimate == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "canEstimate field required"));
        }

        user.setCanEstimate(canEstimate);
        userRepository.save(user);
        
        return ResponseEntity.ok(Map.of(
                "message", canEstimate ? "User can now submit estimates" : "User blocked from estimates",
                "canEstimate", canEstimate
        ));
    }

    // ============================================================
    // POST /api/admin/users/{id}/reset-password - Send password reset
    // ============================================================

    @PostMapping("/users/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> resetPassword(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // TODO: Implement email sending with temporary password
        // For now, just generate and save a new password
        String tempPassword = "temp" + System.currentTimeMillis();
        user.setPassword(passwordEncoder.encode(tempPassword));
        userRepository.save(user);
        
        return ResponseEntity.ok(Map.of(
                "message", "Password reset. Temporary password: " + tempPassword,
                "tempPassword", tempPassword
        ));
    }

    // ============================================================
    // DTOs
    // ============================================================

    public record UserDTO(
            Long id,
            String name,
            String email,
            String role,
            Boolean canEstimate
    ) {}

    public record CreateUserRequest(
            String name,
            String email,
            String password,
            String role
    ) {}

    public record UpdateUserRequest(
            String name,
            String email,
            String role
    ) {}
}