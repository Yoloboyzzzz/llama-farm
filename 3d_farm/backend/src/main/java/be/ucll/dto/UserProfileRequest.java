package be.ucll.dto;

public record UserProfileRequest(
    String name,
    String avatarEmoji,
    String avatarColor,
    boolean darkMode,
    Boolean notificationsEnabled  // Optional - only for admins
) {}