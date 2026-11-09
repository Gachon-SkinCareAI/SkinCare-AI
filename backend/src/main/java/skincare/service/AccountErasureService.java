package skincare.service;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import skincare.dto.UserWithdrawRequestDto;
import skincare.repository.UserRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
// AccountErasureService 비즈니스 로직 처리
public class AccountErasureService {
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final jakarta.persistence.EntityManager entityManager;

    @Transactional
    public void erase(String owner, UserWithdrawRequestDto request) {
        if (!"탈퇴".equals(request.getConfirmation()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "삭제 범위를 확인하고 '탈퇴'를 입력해 주세요.");
        var u =
                users.lockForAnalysisCreation(owner)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        // Serialize against all account writes; DB photos/answers are removed in the same
        // transaction.
        entityManager.flush();
        jdbc.update(
                "INSERT INTO account_erasure_receipts(id,user_id,requested_at) VALUES"
                        + " (?,?,CURRENT_TIMESTAMP)",
                UUID.randomUUID().toString(),
                owner);
        for (String sql : DELETE_OWNED) jdbc.update(sql, owner);
        // Historical email tokens have no owner key: only remove when no other account uses the
        // email.
        if (u.getEmail() != null
                && jdbc.queryForObject(
                                "SELECT COUNT(*) FROM users WHERE email=? AND user_id<>?",
                                Long.class,
                                u.getEmail(),
                                owner)
                        == 0) {
            jdbc.update("DELETE FROM password_reset_tokens WHERE email=?", u.getEmail());
            jdbc.update("DELETE FROM email_verification_tokens WHERE email=?", u.getEmail());
        }
        jdbc.update("DELETE FROM users WHERE user_id=?", owner);
        entityManager
                .clear(); // Never flush stale managed user/record instances back after erasure.
    }

    // Also used verbatim by the backup restoration erasure gate (documented and tested).
    public static final String[] DELETE_OWNED = {
        "DELETE FROM inquiry_answers WHERE inquiry_id IN (SELECT inquiry_id FROM inquiries WHERE"
                + " user_id=?)",
        "DELETE FROM inquiries WHERE user_id=?",
        "DELETE FROM recoveries WHERE diagnosis_id IN (SELECT diagnosis_id FROM diagnoses WHERE"
                + " user_id=?)",
        "DELETE FROM diagnosis_metrics WHERE diagnosis_id IN (SELECT diagnosis_id FROM diagnoses"
                + " WHERE user_id=?)",
        "DELETE FROM diagnosis_action_histories WHERE diagnosis_id IN (SELECT diagnosis_id FROM"
                + " diagnoses WHERE user_id=?)",
        "DELETE FROM diagnoses WHERE user_id=?",
        "DELETE FROM analysis_answers WHERE session_id IN (SELECT id FROM analysis_sessions WHERE"
                + " user_id=?)",
        "DELETE FROM analysis_sessions WHERE user_id=?",
        "DELETE FROM consent_events WHERE user_id=?",
        "DELETE FROM user_refresh_tokens WHERE user_id=?",
        "DELETE FROM refresh_tokens WHERE subject_id=? AND role='USER'",
        "DELETE FROM user_change_histories WHERE user_id=?",
        "DELETE FROM user_settings WHERE user_id=?"
    };
}
