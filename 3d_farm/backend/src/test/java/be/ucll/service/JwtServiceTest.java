package be.ucll.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        // Use a fixed test secret (Base64-encoded 256-bit key)
        jwtService = new JwtService("dGVzdC1zZWNyZXQta2V5LWZvci11bml0LXRlc3RzLTMyYg==");
    }

    // ============================================================
    // generateToken Tests
    // ============================================================

    @Test
    void generateToken_ShouldReturnNonNullNonBlankToken() {
        String token = jwtService.generateToken("user@example.com");

        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    void generateToken_ShouldProduceThreePartJwtStructure() {
        // JWT format: header.payload.signature
        String token = jwtService.generateToken("user@example.com");
        String[] parts = token.split("\\.");

        assertEquals(3, parts.length, "JWT must have exactly 3 dot-separated parts");
    }

    @Test
    void generateToken_DifferentEmails_ShouldProduceDifferentTokens() {
        String token1 = jwtService.generateToken("alice@example.com");
        String token2 = jwtService.generateToken("bob@example.com");

        assertNotEquals(token1, token2);
    }

    @Test
    void generateToken_SameEmail_ShouldProduceUniqueTokensOverTime() throws InterruptedException {
        // Because issuedAt is millisecond-based, two tokens generated at
        // different times must differ
        String token1 = jwtService.generateToken("user@example.com");
        Thread.sleep(5);
        String token2 = jwtService.generateToken("user@example.com");

        assertNotEquals(token1, token2);
    }

    // ============================================================
    // extractEmail Tests
    // ============================================================

    @Test
    void extractEmail_FromValidToken_ShouldReturnCorrectEmail() {
        String email = "user@example.com";
        String token = jwtService.generateToken(email);

        String extracted = jwtService.extractEmail(token);

        assertEquals(email, extracted);
    }

    @Test
    void extractEmail_PreservesEmailExactly() {
        String email = "CAPS.user+tag@sub.domain.org";
        String token = jwtService.generateToken(email);

        assertEquals(email, jwtService.extractEmail(token));
    }

    @Test
    void extractEmail_FromTamperedSignature_ShouldThrowException() {
        String token = jwtService.generateToken("user@example.com");
        // Replace the signature part with garbage
        String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "invalidsignatureXXX";

        assertThrows(Exception.class, () -> jwtService.extractEmail(tampered));
    }

    @Test
    void extractEmail_FromRandomString_ShouldThrowException() {
        assertThrows(Exception.class, () -> jwtService.extractEmail("not.a.real.jwt"));
    }

    @Test
    void extractEmail_FromEmptyString_ShouldThrowException() {
        assertThrows(Exception.class, () -> jwtService.extractEmail(""));
    }

    // ============================================================
    // isValid Tests
    // ============================================================

    @Test
    void isValid_WithFreshToken_ShouldReturnTrue() {
        String token = jwtService.generateToken("user@example.com");

        assertTrue(jwtService.isValid(token));
    }

    @Test
    void isValid_WithTamperedSignature_ShouldReturnFalse() {
        String token = jwtService.generateToken("user@example.com");
        String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "invalidsignatureXXX";

        assertFalse(jwtService.isValid(tampered));
    }

    @Test
    void isValid_WithMalformedString_ShouldReturnFalse() {
        assertFalse(jwtService.isValid("this.is.notajwt"));
    }

    @Test
    void isValid_WithEmptyString_ShouldReturnFalse() {
        assertFalse(jwtService.isValid(""));
    }

    @Test
    void isValid_WithTruncatedToken_ShouldReturnFalse() {
        String token = jwtService.generateToken("user@example.com");
        // Keep only the first two parts (header.payload — no signature)
        String truncated = token.substring(0, token.lastIndexOf('.'));

        assertFalse(jwtService.isValid(truncated));
    }

    @Test
    void isValid_TokenGeneratedByDifferentKeyInstance_ShouldReturnFalse() {
        // A second JwtService instance generates its own random key.
        // A token from one instance is invalid on the other.
        JwtService otherService = new JwtService("b3RoZXItc2VjcmV0LWtleS1mb3ItdW5pdC10ZXN0cy0zMg==");
        String foreignToken = otherService.generateToken("user@example.com");

        assertFalse(jwtService.isValid(foreignToken));
    }
}
