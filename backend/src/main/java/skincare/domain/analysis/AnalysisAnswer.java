package skincare.domain.analysis;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.NoArgsConstructor;

import skincare.domain.global.BaseTimeEntity;

import java.util.UUID;

@Entity
@Table(
        name = "analysis_answers",
        uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "client_request_id"}))
@Getter
@NoArgsConstructor
// AnalysisAnswer 도메인 상태 관리
public class AnalysisAnswer extends BaseTimeEntity {
    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "session_id", nullable = false, length = 36)
    private String sessionId;

    @Column(name = "client_request_id", nullable = false, length = 36)
    private String clientRequestId;

    @Column(nullable = false, length = 100)
    private String questionId;

    @Column(length = 100)
    private String answer;

    @Column(length = 2000)
    private String questionText;

    @Column(length = 200)
    private String answerLabel;

    @Column(length = 2000)
    private String userText;

    @Column(nullable = false)
    private int turn;

    public AnalysisAnswer(
            String sessionId,
            String requestId,
            String questionId,
            String answer,
            String userText,
            int turn,
            String questionText,
            String answerLabel) {
        id = UUID.randomUUID().toString();
        this.sessionId = sessionId;
        clientRequestId = requestId;
        this.questionId = questionId;
        this.answer = answer;
        this.userText = userText;
        this.turn = turn;
        this.questionText = questionText;
        this.answerLabel = answerLabel;
    }
}
