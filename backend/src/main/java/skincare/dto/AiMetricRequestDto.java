package skincare.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import lombok.Data;

// 관리자 AI 성능 지표 등록·수정 요청 DTO (PUT /api/wc/admin/ai/metrics/{metricKey})
@Data
// AiMetricRequestDto 요청과 응답 데이터
public class AiMetricRequestDto {

    // 지표 수치 (ex. 93.1). 단위가 %이면 0~100
    @NotNull(message = "지표 값은 필수 입력 항목입니다.")
    @PositiveOrZero(message = "지표 값은 0 이상이어야 합니다.")
    private Double value;

    // 단위 (ex. %). 없으면 비워 둔다
    @Size(max = 20, message = "단위는 20자 이하여야 합니다.")
    private String unit;

    // 지표 설명 (ex. 잠금 test 177장 기준)
    @Size(max = 255, message = "설명은 255자 이하여야 합니다.")
    private String description;
}
