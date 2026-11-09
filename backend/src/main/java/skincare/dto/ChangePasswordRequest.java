package skincare.dto;

// ChangePasswordRequest 요청과 응답 데이터
public record ChangePasswordRequest(String currentPassword, String newPassword) {}
