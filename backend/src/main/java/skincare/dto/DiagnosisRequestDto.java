package skincare.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import skincare.domain.diagnosis.entity.ENUM.BodyPart;

@Getter
@Setter
@NoArgsConstructor
public class DiagnosisRequestDto {

    @NotNull(message = "촬영 신체 부위는 필수 선택 항목입니다.")
    private BodyPart bodyPart; // ARM, LEG, FACE, TORSO 등

    private String symptoms;   // 자가 증상 메모
}