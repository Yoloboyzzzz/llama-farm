package be.ucll.dto;

public record UserProfileResponse(
    Long userId,
    String name,
    String email,
    String role,
    String avatarEmoji,
    String avatarColor,
    boolean darkMode,
    boolean notificationsEnabled
) {}