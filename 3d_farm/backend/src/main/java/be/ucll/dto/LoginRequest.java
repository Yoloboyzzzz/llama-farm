package be.ucll.dto;

public record LoginRequest(
    String email,
    String password
) {
    public String username() {
        return email;  // For compatibility
    }
    
    public String getPassword() {
        return password;
    }
}