package be.ucll.model;

import jakarta.persistence.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "users")
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id") 
    private Long userId;

    private String name;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    private Role role = Role.USER;

    @Column(name = "avatar_emoji")
    private String avatarEmoji = "🦙";

    @Column(name = "avatar_color")
    private String avatarColor = "#f97316";

    @Column(name = "dark_mode")
    private boolean darkMode = false;

    // ✅ NEW: Can this user submit cost estimates?
    @Column(name = "can_estimate", nullable = false)
    private boolean canEstimate = true;

    @Column(name = "notifications_enabled", nullable = false)
    private boolean notificationsEnabled = true;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "verification_token")
    private String verificationToken;

    public boolean isNotificationsEnabled() {
        return notificationsEnabled;
    }

    public void setNotificationsEnabled(boolean notificationsEnabled) {
        this.notificationsEnabled = notificationsEnabled;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public String getVerificationToken() {
        return verificationToken;
    }

    public void setVerificationToken(String verificationToken) {
        this.verificationToken = verificationToken;
    }

    // ============ NESTED ENUM ============
    public enum Role {
        ADMIN, USER;

        public GrantedAuthority toGrantedAuthority() {
            return new SimpleGrantedAuthority("ROLE_" + name());
        }
    }

    // ============ CONSTRUCTORS ============
    public User() {}

    public User(String name, String email, String password, Role role) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.role = role;
        this.canEstimate = true; // Default to allowed
    }

    // ============ GETTERS & SETTERS ============
    public Long getUserId() { 
        return userId; 
    }
    
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() { 
        return name; 
    }

    public String getAvatarEmoji() {
        return avatarEmoji != null ? avatarEmoji : "🦙";
    }

    public void setAvatarEmoji(String avatarEmoji) {
        this.avatarEmoji = avatarEmoji;
    }

    public String getAvatarColor() {
        return avatarColor != null ? avatarColor : "#f97316";
    }

    public void setAvatarColor(String avatarColor) {
        this.avatarColor = avatarColor;
    }

    public boolean isDarkMode() {
        return darkMode;
    }

    public void setDarkMode(boolean darkMode) {
        this.darkMode = darkMode;
    }

    // ✅ NEW: Estimate access getters/setters
    public boolean getCanEstimate() {
        return canEstimate;
    }

    public void setCanEstimate(boolean canEstimate) {
        this.canEstimate = canEstimate;
    }
    
    public void setName(String name) { 
        this.name = name; 
    }

    public String getEmail() { 
        return email; 
    }
    
    public void setEmail(String email) { 
        this.email = email; 
    }

    @Override
    public String getPassword() { 
        return password; 
    }
    
    public void setPassword(String password) { 
        this.password = password; 
    }

    public Role getRole() { 
        return role; 
    }
    
    public void setRole(Role role) { 
        this.role = role; 
    }

    // ============ UTILITY METHODS ============
    
    // ✅ NEW: Check if user is admin
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    // ============ UserDetails Implementation ============
    
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(role.toGrantedAuthority());
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}