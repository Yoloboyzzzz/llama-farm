package be.ucll.model;

import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Locale;

public enum RoleName {

    ADMIN,      // Full system access
    USER;       // Regular user - can create jobs

    public GrantedAuthority toGrantedAuthority() {
        return new SimpleGrantedAuthority("ROLE_" + name());
    }

    @Override
    @JsonValue
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}