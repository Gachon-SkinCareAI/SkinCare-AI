package skincare.service;

import jakarta.annotation.PostConstruct;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import skincare.exception.CustomException;
import skincare.exception.ErrorCode;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

// 업로드 이미지(상처 사진, 프로필 사진) 파일 저장소 서비스
// 이미지 원본은 DB에 저장하지 않고 서버 디스크(file.upload-dir)에 저장하며, DB에는 저장 경로(storage key)만 기록
// 상처 사진은 민감정보이므로 정적 경로로 공개하지 않고, 소유자/관리자 권한을 확인하는 API를 통해서만 제공
@Slf4j
@Service
// FileStorageService 비즈니스 로직 처리
public class FileStorageService {

    // 허용 이미지 확장자 (스마트폰 기본 촬영 포맷 HEIC 포함)
    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "heic", "heif");

    // 업로드 파일 저장 루트 경로
    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    private Path rootPath; // 정규화된 저장 루트 경로

    // Resolve at startup; create directories only for an actual legacy file upload.
    // The active analysis API stores photos in MySQL, including read-only containers.
    @PostConstruct
    protected void init() throws IOException {
        this.rootPath = Path.of(uploadDir).toAbsolutePath().normalize();
        log.info("[FileStorage] 업로드 파일 저장 경로: {}", rootPath);
    }

    // 이미지 파일 검증 후 저장, 저장 경로(storage key) 반환 (ex. diagnoses/20261101/3fa85f64....jpg)
    public String storeImage(MultipartFile file, String category) {
        String extension = validateImage(file);

        String datePath = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String storageKey = category + "/" + datePath + "/" + UUID.randomUUID() + "." + extension;
        Path target = resolve(storageKey);

        try (InputStream inputStream = file.getInputStream()) {
            Files.createDirectories(target.getParent());
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("[FileStorage] 이미지 저장 실패 (storageKey: {}): {}", storageKey, e.getMessage());
            throw new CustomException(ErrorCode.DIAGNOSIS_SAVE_FAILED, "이미지 파일 저장에 실패했습니다.");
        }
        return storageKey;
    }

    // 저장된 파일을 Resource로 조회
    public Resource load(String storageKey) {
        try {
            Path path = resolve(storageKey);
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new CustomException(ErrorCode.IMAGE_FILE_NOT_FOUND);
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new CustomException(ErrorCode.IMAGE_FILE_NOT_FOUND);
        }
    }

    // 파일 확장자 기반 Content-Type 추정 (응답 헤더용)
    public MediaType getMediaType(String storageKey) {
        return MediaTypeFactory.getMediaType(storageKey).orElse(MediaType.APPLICATION_OCTET_STREAM);
    }

    // 저장 파일 삭제 (DB 저장 실패 등으로 파일만 남는 경우 정리용, 실패해도 예외를 던지지 않음)
    public void deleteQuietly(String storageKey) {
        if (storageKey == null) return;
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (Exception e) {
            log.warn("[FileStorage] 파일 삭제 실패 (storageKey: {}): {}", storageKey, e.getMessage());
        }
    }

    // 외부 URL(구글 프로필 사진 등)이 아닌 서버 저장 파일인지 여부
    public static boolean isStoredFile(String value) {
        return value != null && !value.startsWith("http://") && !value.startsWith("https://");
    }

    // 업로드 이미지 검증 (파일 누락, 확장자/Content-Type) 후 소문자 확장자 반환
    private String validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.IMAGE_REQUIRED);
        }

        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        int dotIndex = filename.lastIndexOf('.');
        String extension =
                dotIndex >= 0 ? filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT) : "";

        String contentType =
                file.getContentType() != null ? file.getContentType().toLowerCase(Locale.ROOT) : "";
        boolean imageContentType =
                contentType.startsWith("image/")
                        || contentType.equals(MediaType.APPLICATION_OCTET_STREAM_VALUE);

        if (!ALLOWED_EXTENSIONS.contains(extension) || !imageContentType) {
            log.warn(
                    "[FileStorage] 지원하지 않는 이미지 형식 (filename: {}, contentType: {})",
                    filename,
                    contentType);
            throw new CustomException(ErrorCode.UNSUPPORTED_IMAGE_FORMAT);
        }
        return extension.equals("jpeg") ? "jpg" : extension;
    }

    // storage key를 실제 경로로 변환 (../ 등을 이용한 저장 루트 밖 경로 접근 차단)
    private Path resolve(String storageKey) {
        Path path = rootPath.resolve(storageKey).normalize();
        if (!path.startsWith(rootPath)) {
            throw new CustomException(ErrorCode.IMAGE_FILE_NOT_FOUND);
        }
        return path;
    }
}
