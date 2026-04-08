package be.ucll.dto;

import java.util.List;

public record AuthMeResponse(
    String email,
    String role,
    List<String> roles,
    Long userId,
    String name
) {}