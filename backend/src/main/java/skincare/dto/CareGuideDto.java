package skincare.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import skincare.dto.ai.AiGuideResponseDto;

import java.util.List;

// 분석 결과 케어 가이드 응답 DTO (분석 응답의 care_guide 객체)
// 공인 기관 케어 카드 원문과 출처, 피해야 할 행동을 그대로 제공 (AI가 케어 문장을 생성하지 않음)
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CareGuideDto {

    private String firstAid;                           // 응급처치 안내 (케어 카드 원문)
    private String precautions;                        // 주의사항 (피해야 할 행동)
    private List<AiGuideResponseDto.CareCard> cards;   // 공인 케어 카드 목록 (출처, 출처 URL 포함)
    private List<AiGuideResponseDto.AvoidItem> avoid;  // 피해야 할 행동 목록
    private String reason;                             // 카드가 없을 때 사유 (no_card, no_level, guide_unavailable)
}
