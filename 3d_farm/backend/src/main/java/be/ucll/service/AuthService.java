package be.ucll.service;

import be.ucll.dto.LoginRequest;
import be.ucll.dto.RegisterRequest;
import be.ucll.model.User;
import be.ucll.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            EmailService emailService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailService = emailService;
    }

    public String login(LoginRequest request) {
        String email = request.email().toLowerCase();

        System.out.println("🔍 Login attempt for email: " + email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    System.out.println("❌ User not found: " + email);
                    return new ResponseStatusException(
                            HttpStatus.UNAUTHORIZED,
                            "Invalid credentials"
                    );
                });

        System.out.println("✅ User found: " + user.getEmail());
        System.out.println("🔑 Checking password...");

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            System.out.println("❌ Password doesn't match");
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid credentials"
            );
        }

        if (!user.isEmailVerified()) {
            System.out.println("❌ Email not verified: " + email);
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Email not verified. Please check your inbox and click the verification link."
            );
        }

        System.out.println("✅ Password matches! Generating token...");
        String token = jwtService.generateToken(user.getEmail());
        System.out.println("✅ Token generated: " + token.substring(0, 20) + "...");

        return token;
    }

    @Transactional
    public void register(RegisterRequest request) {
        // Check if email already exists
        String email = request.email().toLowerCase();

        if (userRepository.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email already in use"
            );
        }

        // Create new user (unverified)
        User user = new User();
        user.setName(request.name());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(User.Role.USER);
        user.setEmailVerified(false);
        user.setVerificationToken(UUID.randomUUID().toString());

        userRepository.save(user);

        // Send verification email
        emailService.sendVerificationEmail(user.getEmail(), user.getVerificationToken());

        System.out.println("✅ User registered (pending verification): " + request.email());
    }

    @Transactional
    public void verifyEmail(String token) {
        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid or expired verification link"
                ));

        user.setEmailVerified(true);
        user.setVerificationToken(null); // consume the token
        userRepository.save(user);

        System.out.println("✅ Email verified for: " + user.getEmail());
    }
}
