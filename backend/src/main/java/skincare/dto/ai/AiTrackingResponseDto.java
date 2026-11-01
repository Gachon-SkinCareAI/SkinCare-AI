package skincare.dto.ai;

// AI 서버 두 사진 비교 결과 DTO (POST /api/v1/ai/tracking 응답)
// 임상적 「호전」 판단이 아닌 박스 면적 비율 차이 참고값이며, 박스 한쪽이 없으면 uncomparable
public record AiTrackingResponseDto(
        Double areaRatioDelta, // 박스 면적 비율 차
        String trendHint,      // smaller, similar, larger, uncomparable
        String note            // 촬영 조건 보정이 없다는 안내 문구
) {
}
