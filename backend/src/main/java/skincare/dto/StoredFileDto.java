package skincare.dto;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

// 서버에 저장된 파일(상처 사진, 프로필 사진) 조회 결과 DTO
public record StoredFileDto(
        Resource resource,   // 파일 리소스
        MediaType mediaType  // 응답 Content-Type
) {

    // 이미지 바이너리 응답으로 변환 (민감 이미지이므로 브라우저/프록시 캐시 저장 금지)
    public ResponseEntity<Resource> toResponseEntity() {
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header("Cache-Control", "private, no-store")
                .body(resource);
    }
}
