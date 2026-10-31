package skincare.external;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import skincare.domain.diagnosis.entity.ENUM.BodyPart;
import skincare.dto.AiInferenceResponseDto;
import skincare.exception.CustomException;
import skincare.exception.ErrorCode;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiInferenceClient {

    private final RestTemplate restTemplate;

    @Value("${AI_SERVER_URL}")
    private String aiServerUrl;

    /**
     * AI 모델 서버로 상처 이미지 및 부위 정보를 전송하여 추론 결과를 받아옵니다.
     */
    public AiInferenceResponseDto requestInference(MultipartFile imageFile, BodyPart bodyPart) {
        log.info("[AI Inference] AI 모델 서버({})로 추론 요청 전송...", aiServerUrl);

        try {
            // 1. Multipart Header 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            // 2. Multipart Body 생성 (파일 및 추가 폼 데이터)
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

            // MultipartFile을 RestTemplate이 전송 가능한 ByteArrayResource로 변환
            ByteArrayResource imageResource = new ByteArrayResource(imageFile.getBytes()) {
                @Override
                public String getFilename() {
                    return imageFile.getOriginalFilename() != null ? imageFile.getOriginalFilename() : "wound_image.jpg";
                }
            };

            body.add("file", imageResource);
            body.add("bodyPart", bodyPart.name());

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            // 3. AI 서버로 HTTP POST 요청
            ResponseEntity<AiInferenceResponseDto> response = restTemplate.postForEntity(
                    aiServerUrl,
                    requestEntity,
                    AiInferenceResponseDto.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                log.info("[AI Inference] AI 추론 성공: {}", response.getBody().getSuspectedDisease());
                return response.getBody();
            } else {
                log.error("[AI Inference] AI 서버 응답 이상: Status {}", response.getStatusCode());
                throw new CustomException(ErrorCode.AI_SERVER_ERROR);
            }

        } catch (Exception e) {
            log.error("[AI Inference] AI 서버 통신 도중 예외 발생: {}", e.getMessage(), e);
            throw new CustomException(ErrorCode.AI_SERVER_ERROR, "AI 분석 서버와의 통신에 실패했습니다.");
        }
    }
}