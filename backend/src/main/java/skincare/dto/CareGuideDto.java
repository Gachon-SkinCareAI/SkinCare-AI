package skincare.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Getter;

import skincare.dto.ai.AiGuideResponseDto;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

// 분석 결과 케어 가이드 응답 DTO (분석 응답의 care_guide 객체)
// 공인 기관 케어 카드 원문과 출처, 피해야 할 행동을 그대로 제공 (AI가 케어 문장을 생성하지 않음)
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
// CareGuideDto 요청과 응답 데이터
public class CareGuideDto {

    private String firstAid; // 응급처치 안내 (케어 카드 원문)
    private String precautions; // 주의사항 (피해야 할 행동)
    private List<AiGuideResponseDto.CareCard> cards; // 공인 케어 카드 목록 (출처, 출처 URL 포함)
    private List<AiGuideResponseDto.AvoidItem> avoid; // 피해야 할 행동 목록
    private String reason; // 카드가 없을 때 사유 (no_card, no_level, guide_unavailable)

    // AI 케어 카드 응답을 케어 가이드 응답으로 변환 (카드 원문/피해야 할 행동을 안내 문구로 연결)
    public static CareGuideDto from(AiGuideResponseDto guide) {
        List<AiGuideResponseDto.CareCard> cards = guide.cards() != null ? guide.cards() : List.of();
        List<AiGuideResponseDto.AvoidItem> avoid =
                guide.avoid() != null ? guide.avoid() : List.of();
        return CareGuideDto.builder()
                .firstAid(joinFirstAid(cards))
                .precautions(joinPrecautions(avoid))
                .cards(cards)
                .avoid(avoid)
                .reason(guide.reason())
                .build();
    }

    // 케어 카드 원문을 응급처치 안내 문구로 연결
    public static String joinFirstAid(List<AiGuideResponseDto.CareCard> cards) {
        return cards.stream()
                .map(AiGuideResponseDto.CareCard::body)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n\n"));
    }

    // 피해야 할 행동 목록을 주의사항 문구로 연결
    public static String joinPrecautions(List<AiGuideResponseDto.AvoidItem> avoid) {
        return avoid.stream()
                .map(AiGuideResponseDto.AvoidItem::label)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"));
    }
}
