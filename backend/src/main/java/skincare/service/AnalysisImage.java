package skincare.service;

import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public record AnalysisImage(byte[] bytes, String contentType) {
    public static AnalysisImage read(MultipartFile upload) {
        if (upload == null || upload.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사진이 필요합니다.");
        if (upload.getSize() > 8 * 1024 * 1024) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "사진은 8MB 이하여야 합니다.");
        try {
            byte[] bytes;
            try (var stream = upload.getInputStream()) { bytes = stream.readNBytes(8 * 1024 * 1024 + 1); }
            if (bytes.length > 8 * 1024 * 1024) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw new IOException("Unsupported image");
                var reader = readers.next();
                try {
                    reader.setInput(input);
                    String format = reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                    if (!java.util.Set.of("png", "jpeg", "jpg").contains(format)) throw new IOException("Unsupported format");
                    if ((long) reader.getWidth(0) * reader.getHeight(0) > 20_000_000) throw new IOException("Too many pixels");
                    var image = reader.read(0);
                    if (image == null) throw new IOException("Invalid image");
                    var output = new ByteArrayOutputStream();
                    // Re-encode without EXIF/location metadata. The mobile client normalizes orientation before upload.
                    String normalized = format.equals("png") ? "png" : "jpeg";
                    if (!ImageIO.write(image, normalized, output)) throw new IOException("Cannot encode image");
                    if (output.size() > 8 * 1024 * 1024) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
                    return new AnalysisImage(output.toByteArray(), "image/" + normalized);
                } finally { reader.dispose(); }
            }
        } catch (IOException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "사진을 읽을 수 없습니다. JPEG 또는 PNG 사진을 사용해 주세요.");
        }
    }
}
