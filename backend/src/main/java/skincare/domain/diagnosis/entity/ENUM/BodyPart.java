package skincare.domain.diagnosis.entity.ENUM;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 촬영 신체 부위 (ARM: 팔, LEG: 다리, FACE: 얼굴, TORSO: 몸통, HAND_FOOT: 손/발, OTHER: 기타)
@Getter
@RequiredArgsConstructor
// BodyPart 도메인 상태 관리
public enum BodyPart {
    ARM("팔"),
    LEG("다리"),
    FACE("얼굴"),
    TORSO("몸통"),
    HAND_FOOT("손/발"),
    OTHER("기타");

    private final String label; // 화면 표시용 한글 명칭
}
