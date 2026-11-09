package skincare.dto;

import jakarta.validation.constraints.NotNull;

import lombok.Data;

// 분석 사진 학습 활용 동의 변경 요청 DTO (PATCH /api/wc/diagnoses/{diagnosisId}/training-use)
@Data
// TrainingUseRequestDto 요청과 응답 데이터
public class TrainingUseRequestDto {

    @NotNull(message = "training_use는 필수 입력 항목입니다.")
    private Boolean trainingUse; // true: 재학습 후보 활용 동의, false: 동의 철회
}
