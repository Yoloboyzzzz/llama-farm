package be.ucll.service;

import be.ucll.dto.LoginRequest;
import be.ucll.dto.RegisterRequest;
import be.ucll.model.User;
import be.ucll.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setUserId(1L);
        testUser.setName("Test User");
        testUser.setEmail("test@example.com");
        testUser.setPassword("$2a$10$encodedPassword");
        testUser.setRole(User.Role.USER);
    }

    // ============================================================
    // login Tests
    // ============================================================

    @Test
    void login_WithValidCredentials_ShouldReturnToken() {
        LoginRequest request = new LoginRequest("test@example.com", "password123");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password123", testUser.getPassword())).thenReturn(true);
        when(jwtService.generateToken("test@example.com")).thenReturn("jwt.token.here");

        String token = authService.login(request);

        assertNotNull(token);
        assertEquals("jwt.token.here", token);
        verify(jwtService, times(1)).generateToken("test@example.com");
    }

    @Test
    void login_WithUnknownEmail_ShouldThrowUnauthorized() {
        LoginRequest request = new LoginRequest("ghost@example.com", "password123");
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.login(request));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verify(jwtService, never()).generateToken(anyString());
    }

    @Test
    void login_WithWrongPassword_ShouldThrowUnauthorized() {
        LoginRequest request = new LoginRequest("test@example.com", "wrongpassword");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongpassword", testUser.getPassword())).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.login(request));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verify(jwtService, never()).generateToken(anyString());
    }

    @Test
    void login_ShouldUseEmailAsTokenSubject() {
        LoginRequest request = new LoginRequest("test@example.com", "password123");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(jwtService.generateToken("test@example.com")).thenReturn("token");

        authService.login(request);

        // Verify the token is generated for exactly the user's email
        verify(jwtService, times(1)).generateToken("test@example.com");
        verify(jwtService, never()).generateToken(argThat(e -> !e.equals("test@example.com")));
    }

    // ============================================================
    // register Tests
    // ============================================================

    @Test
    void register_WithNewEmail_ShouldSaveUser() {
        RegisterRequest request = new RegisterRequest("New User", "new@example.com", "securePass");
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("securePass")).thenReturn("$2a$10$hashedPassword");

        authService.register(request);

        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void register_NewUser_ShouldDefaultToUserRole() {
        RegisterRequest request = new RegisterRequest("New User", "new@example.com", "securePass");
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            assertEquals(User.Role.USER, saved.getRole());
            return saved;
        });

        authService.register(request);

        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void register_ShouldHashPasswordAndNeverStorePlaintext() {
        String rawPassword = "plaintextPassword";
        RegisterRequest request = new RegisterRequest("User", "user@example.com", rawPassword);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(rawPassword)).thenReturn("$2a$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            assertNotEquals(rawPassword, saved.getPassword());
            assertEquals("$2a$hashed", saved.getPassword());
            return saved;
        });

        authService.register(request);

        verify(passwordEncoder, times(1)).encode(rawPassword);
    }

    @Test
    void register_WithExistingEmail_ShouldThrowConflict() {
        RegisterRequest request = new RegisterRequest("Test User", "test@example.com", "password");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.register(request));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_ShouldPersistNameAndEmail() {
        RegisterRequest request = new RegisterRequest("Alice", "alice@example.com", "pass");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            assertEquals("Alice", saved.getName());
            assertEquals("alice@example.com", saved.getEmail());
            return saved;
        });

        authService.register(request);

        verify(userRepository, times(1)).save(any(User.class));
    }
}
