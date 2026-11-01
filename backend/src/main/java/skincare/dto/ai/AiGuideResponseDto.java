package skincare.dto.ai;

import java.util.List;

// AI 서버 공인 케어 카드 조회 결과 DTO (POST /api/v1/ai/guide 응답)
// body는 공인 문서 원문만 포함하며, source_url이 없는 카드는 AI 서버가 반환하지 않음
public record AiGuideResponseDto(
        List<CareCard> cards,  // 공인 케어 카드 목록
        List<AvoidItem> avoid, // 하지 말아야 할 행동 목록
        String reason          // 카드가 없을 때 사유 (no_card, no_level)
) {

    // 공인 케어 카드 (ex. 질병관리청 화상 응급처치 원문)
    public record CareCard(
            String id,         // 카드 ID (ex. kdca.burn.cool)
            String title,      // 카드 제목
            String body,       // 공인 문서 원문
            String source,     // 출처 기관
            String sourceUrl   // 출처 URL
    ) {
    }

    // 피해야 할 행동 항목
    public record AvoidItem(
            String id,    // 항목 ID (ex. no_ice)
            String label  // 표시 문구
    ) {
    }
}
