package community.likelion.growwithusserver.common.file;

import community.likelion.growwithusserver.common.file.exception.FileNotSupportedException;
import community.likelion.growwithusserver.common.file.exception.FileUploadFailedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

// 업로드 파일을 검증하고 저장소에 올린 뒤 URL을 돌려준다. 위반 시 C4001.
@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadService {
    // 사진: jpg, png 10MB 이하
    public static final long MAX_IMAGE_SIZE = 10L * 1024 * 1024;
    // 음성: m4a, wav 60초 이하. 크기는 multipart 상한(20MB)과 같게 둔다.
    public static final long MAX_AUDIO_SIZE = 20L * 1024 * 1024;
    public static final Duration MAX_AUDIO_DURATION = Duration.ofSeconds(60);

    private final FileStorage fileStorage;

    public String uploadImage(MultipartFile file, String directory) {
        byte[] bytes = readBytes(file, MAX_IMAGE_SIZE);
        String extension = extensionOf(file);

        String contentType;
        if ((extension.equals("jpg") || extension.equals("jpeg")) && isJpeg(bytes)) {
            contentType = "image/jpeg";
        } else if (extension.equals("png") && isPng(bytes)) {
            contentType = "image/png";
        } else {
            log.warn("[uploadImage] 지원하지 않는 사진 형식: {}", extension);
            throw new FileNotSupportedException();
        }

        return store(directory, extension, bytes, contentType);
    }

    public String uploadAudio(MultipartFile file, String directory) {
        byte[] bytes = readBytes(file, MAX_AUDIO_SIZE);
        String extension = extensionOf(file);

        Optional<Duration> duration;
        String contentType;
        if (extension.equals("wav")) {
            duration = AudioDurationReader.readWav(bytes);
            contentType = "audio/wav";
        } else if (extension.equals("m4a")) {
            duration = AudioDurationReader.readM4a(bytes);
            contentType = "audio/mp4";
        } else {
            log.warn("[uploadAudio] 지원하지 않는 음성 형식: {}", extension);
            throw new FileNotSupportedException();
        }

        // 길이를 읽을 수 없는 파일은 형식이 깨진 것으로 본다
        if (duration.isEmpty() || duration.get().compareTo(MAX_AUDIO_DURATION) > 0) {
            log.warn("[uploadAudio] 음성 길이 제한 위반: {}", duration.map(Duration::toString).orElse("알 수 없음"));
            throw new FileNotSupportedException();
        }

        return store(directory, extension, bytes, contentType);
    }

    private byte[] readBytes(MultipartFile file, long maxSize) {
        if (file == null || file.isEmpty() || file.getSize() > maxSize) {
            log.warn("[readBytes] 파일이 비었거나 크기 제한 초과: {}", file == null ? null : file.getSize());
            throw new FileNotSupportedException();
        }

        try {
            return file.getBytes();
        } catch (IOException e) {
            log.error("[readBytes] 업로드 파일 읽기 실패", e);
            throw new FileUploadFailedException();
        }
    }

    private String extensionOf(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    // 원본 파일 이름은 쓰지 않고 UUID로 새 이름을 만든다
    private String store(String directory, String extension, byte[] bytes, String contentType) {
        String filename = UUID.randomUUID() + "." + extension;
        String url = fileStorage.store(directory, filename, new ByteArrayInputStream(bytes), bytes.length, contentType);
        log.info("[store] 파일 업로드 완료: {}", url);
        return url;
    }

    private boolean isJpeg(byte[] bytes) {
        return bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF;
    }

    private boolean isPng(byte[] bytes) {
        return bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G'
                && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A;
    }
}
