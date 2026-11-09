package skincare.external;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import skincare.domain.diagnosis.entity.ENUM.BodyPart;
import skincare.dto.ai.*;
import skincare.exception.CustomException;
import skincare.exception.ErrorCode;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

// AI 서버(FastAPI) 내부 REST API 연동 클라이언트
// - 기본 호출 순서: predict → question 반복 → analyze → guide
// - 모든 요청에 Authorization: Bearer {AI_SERVICE_TOKEN} 헤더를 포함하여 메인 서버 요청만 허용되도록 함
@Slf4j
@Component
// AiServiceClient 외부 서비스 연동
public class AiServiceClient {

    private static final String API_PREFIX = "/api/v1/ai";

    private final RestTemplate restTemplate; // AI 서버 전용 RestTemplate (snake_case JSON 변환)

    // AI 서버 기본 주소 (ex. http://127.0.0.1:8000)
    @Value("${ai.server.url:${skincare.ai.base-url:http://127.0.0.1:8000}}")
    private String aiServerUrl;

    // AI 서버 Bearer 인증 토큰 (AI_SERVICE_TOKEN)
    @Value("${ai.server.token:${skincare.ai.token:}}")
    private String aiServiceToken;

    public AiServiceClient(@Qualifier("aiRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // 상처 이미지 분석 요청 (품질 검사 → 환부 탐지 → 유형 분류 → Gate)
    public AiPredictResponseDto predict(MultipartFile imageFile, BodyPart bodyPart) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", toResource(imageFile, "wound_image.jpg"));
        body.add("body_part", bodyPart.name());

        AiProtocol.Prediction wire =
                post(
                        "/predict",
                        multipartEntity(body),
                        AiProtocol.Prediction.class,
                        ErrorCode.MODEL_INFERENCE_FAILED);
        AiPredictResponseDto result = wire.toResult();

        if (result.label() == null) {
            log.error("[AI] predict 응답에 label 누락 (request_id: {})", result.requestId());
            throw new CustomException(
                    ErrorCode.MODEL_INFERENCE_FAILED, "AI 분석 결과에 필수 항목이 누락되었습니다.");
        }
        log.info(
                "[AI] 이미지 분석 완료 (request_id: {}, label: {}, gate_passed: {}, reason: {})",
                result.requestId(),
                result.label(),
                result.gatePassed(),
                result.reason());
        return result;
    }

    // 동적 문진 다음 질문 요청
    public AiQuestionResponseDto question(AiQuestionRequestDto request) {
        AiProtocol.Question wire =
                post(
                        "/question",
                        jsonEntity(AiProtocol.questionBody(request)),
                        AiProtocol.Question.class,
                        ErrorCode.QUESTION_ENGINE_FAILED);
        return wire.toResult();
    }

    // 분석 결과 + 문진 슬롯 기반 위험도 평가 요청 (응급 규칙 우선)
    public AiAnalyzeResponseDto analyze(AiAnalyzeRequestDto request) {
        AiAnalyzeResponseDto result =
                post(
                        "/analyze",
                        jsonEntity(AiProtocol.analyzeBody(request)),
                        AiAnalyzeResponseDto.class,
                        ErrorCode.RISK_ENGINE_FAILED);
        if (result.level() == null) {
            return new AiAnalyzeResponseDto(
                    skincare.domain.diagnosis.entity.ENUM.RiskLevel.UNKNOWN,
                    result.ruleIds(),
                    result.missingSlots(),
                    result.scenarioSource());
        }
        log.info("[AI] 위험도 평가 완료 (level: {}, rule_ids: {})", result.level(), result.ruleIds());
        return result;
    }

    // 위험도에 맞는 공인 케어 카드 조회 요청
    public AiGuideResponseDto guide(AiGuideRequestDto request) {
        return post(
                "/guide",
                jsonEntity(AiProtocol.guideBody(request)),
                AiGuideResponseDto.class,
                ErrorCode.GUIDE_RETRIEVAL_FAILED);
    }

    // 이전/이후 두 사진의 환부 면적 변화 참고값 요청
    public AiTrackingResponseDto tracking(MultipartFile before, MultipartFile after) {
        return tracking(toResource(before, "before.jpg"), toResource(after, "after.jpg"));
    }

    // 이전/이후 두 사진의 환부 면적 변화 참고값 요청 (서버에 저장된 이전 사진 사용)
    public AiTrackingResponseDto tracking(Resource before, MultipartFile after) {
        return tracking(before, toResource(after, "after.jpg"));
    }

    // tracking 요청 공통 처리
    private AiTrackingResponseDto tracking(Resource before, Resource after) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("before", before);
        body.add("after", after);
        return post(
                "/tracking",
                multipartEntity(body),
                AiTrackingResponseDto.class,
                ErrorCode.MODEL_INFERENCE_FAILED);
    }

    // 분석 결과 피드백 적재 요청 (추론/모델 교체는 하지 않음)
    public AiFeedbackResponseDto feedback(AiFeedbackRequestDto request) {
        return post(
                "/feedback",
                jsonEntity(request),
                AiFeedbackResponseDto.class,
                ErrorCode.MODEL_INFERENCE_FAILED);
    }

    // 재학습 잡 등록 요청 (reason: manual, recall_drop, feedback_threshold)
    public AiJobResponseDto retrain(String reason) {
        AiJobResponseDto result =
                post(
                        "/retrain?reason="
                                + org.springframework.web.util.UriUtils.encodeQueryParam(
                                        reason, java.nio.charset.StandardCharsets.UTF_8),
                        jsonEntity(Map.of()),
                        AiJobResponseDto.class,
                        ErrorCode.TRAINING_FAILED);
        log.info("[AI] 재학습 잡 등록 (job_id: {}, status: {})", result.jobId(), result.status());
        return result;
    }

    // 재학습 잡 상태 조회
    public AiJobResponseDto getJob(String jobId) {
        return exchange(
                API_PREFIX + "/jobs/" + jobId,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                AiJobResponseDto.class,
                ErrorCode.RETRAIN_HISTORY_NOT_FOUND);
    }

    // AI 서버 상태 조회 (연결 실패 시 예외 대신 unreachable 상태 반환 → 대시보드가 실패하지 않도록 함)
    public AiHealthResponseDto health() {
        try {
            return exchange(
                    API_PREFIX + "/health",
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders()),
                    AiHealthResponseDto.class,
                    ErrorCode.MODEL_UNAVAILABLE);
        } catch (CustomException e) {
            log.warn("[AI] AI 서버 상태 조회 실패: {}", e.getMessage());
            return AiHealthResponseDto.unreachable();
        }
    }

    // POST 요청 공통 처리
    private <T> T post(
            String path, HttpEntity<?> entity, Class<T> responseType, ErrorCode defaultError) {
        return exchange(API_PREFIX + path, HttpMethod.POST, entity, responseType, defaultError);
    }

    // AI 서버 요청 전송 및 오류 변환 공통 처리
    private <T> T exchange(
            String path,
            HttpMethod method,
            HttpEntity<?> entity,
            Class<T> responseType,
            ErrorCode defaultError) {
        String url = aiServerUrl + path;
        try {
            ResponseEntity<T> response = restTemplate.exchange(url, method, entity, responseType);
            if (response.getBody() == null) {
                log.error("[AI] 응답 본문 없음 ({} {})", method, path);
                throw new CustomException(defaultError);
            }
            return response.getBody();
        } catch (CustomException e) {
            throw e;
        } catch (HttpStatusCodeException e) {
            ErrorCode errorCode = mapHttpError(e, defaultError);
            log.error(
                    "[AI] AI 서버 오류 응답 ({} {}): HTTP {} → {}",
                    method,
                    path,
                    e.getStatusCode().value(),
                    errorCode.getCode());
            throw new CustomException(errorCode);
        } catch (ResourceAccessException e) {
            // 연결 실패 또는 응답 시간 초과
            log.error("[AI] AI 서버 연결 실패 또는 응답 시간 초과 ({} {})", method, path);
            throw new CustomException(ErrorCode.AI_SERVER_TIMEOUT);
        } catch (Exception e) {
            log.error(
                    "[AI] AI 서버 통신 중 예외 발생 ({} {}): {}",
                    method,
                    path,
                    e.getClass().getSimpleName());
            throw new CustomException(defaultError);
        }
    }

    // AI 서버 HTTP 상태 코드를 서비스 오류코드로 변환 (설계서 6.4.12 AI 서버 주요 오류)
    private ErrorCode mapHttpError(HttpStatusCodeException e, ErrorCode defaultError) {
        String body = e.getResponseBodyAsString().toLowerCase(Locale.ROOT);
        return switch (e.getStatusCode().value()) {
            case 401, 403 -> ErrorCode.AI_SERVICE_UNAUTHORIZED; // 토큰 불일치
            case 413 -> ErrorCode.IMAGE_TOO_LARGE; // image_too_large
            case 415 -> ErrorCode.UNSUPPORTED_IMAGE_FORMAT; // unsupported_media
            case 422 ->
                    body.contains("quality") || body.contains("blur") || body.contains("dark")
                            ? ErrorCode.IMAGE_QUALITY_FAILED // 이미지 품질 검사 실패
                            : body.contains("roi") || body.contains("detect")
                                    ? ErrorCode.NO_ROI_DETECTED // 환부 탐지 실패
                                    : ErrorCode.INVALID_AI_REQUEST; // invalid_body
            case 404 -> defaultError;
            case 429 -> ErrorCode.AI_SERVER_BUSY; // busy (동시 추론 한도 초과)
            case 503 -> ErrorCode.MODEL_UNAVAILABLE; // model_unavailable
            default -> defaultError;
        };
    }

    // JSON 요청 엔티티 생성
    private HttpEntity<Object> jsonEntity(Object body) {
        HttpHeaders headers = authHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    // multipart 요청 엔티티 생성
    private HttpEntity<MultiValueMap<String, Object>> multipartEntity(
            MultiValueMap<String, Object> body) {
        HttpHeaders headers = authHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return new HttpEntity<>(body, headers);
    }

    // AI 서버 인증 헤더 생성 (토큰 값은 로그에 기록하지 않음)
    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        if (StringUtils.hasText(aiServiceToken)) {
            headers.setBearerAuth(aiServiceToken);
        }
        return headers;
    }

    // MultipartFile을 RestTemplate이 전송 가능한 ByteArrayResource로 변환
    private ByteArrayResource toResource(MultipartFile file, String defaultFilename) {
        try {
            String filename =
                    file.getOriginalFilename() != null
                            ? file.getOriginalFilename()
                            : defaultFilename;
            return new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };
        } catch (IOException e) {
            throw new CustomException(ErrorCode.IMAGE_REQUIRED, "업로드 이미지를 읽을 수 없습니다.");
        }
    }
}
