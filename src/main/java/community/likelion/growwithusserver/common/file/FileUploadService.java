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
    // 음성: webm, m4a, mp4, wav 60초 이하. 크기는 multipart 상한(20MB)과 같게 둔다.
    public static final long MAX_AUDIO_SIZE = 20L * 1024 * 1024;
    public static final Duration MAX_AUDIO_DURATION = Duration.ofSeconds(60);
    // 길이를 읽을 수 없는 녹음(크롬 webm, 사파리 조각 mp4)은 60초 녹음이 넉넉히 들어가는 크기로만 막는다
    public static final long MAX_UNTIMED_AUDIO_SIZE = 5L * 1024 * 1024;

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
        AudioData audio = inspectAudio(file);
        return store(directory, audio.extension(), audio.bytes(), audio.contentType());
    }

    // 음성 인식 실패 시 저장 파일이 남지 않도록, 저장과 분리해서 먼저 검증할 수 있다.
    public void validateAudio(MultipartFile file) {
        inspectAudio(file);
    }

    public byte[] readStoredAudio(String storedUrl) {
        return fileStorage.readAudio(storedUrl);
    }

    private record AudioData(byte[] bytes, String extension, String contentType) {}

    private AudioData inspectAudio(MultipartFile file) {
        byte[] bytes = readBytes(file, MAX_AUDIO_SIZE);
        String extension = extensionOf(file);

        Optional<Duration> duration;
        String contentType;
        if (extension.equals("wav") && isWav(bytes)) {
            duration = AudioDurationReader.readWav(bytes);
            contentType = "audio/wav";
        } else if ((extension.equals("m4a") || extension.equals("mp4")) && isMp4(bytes)) {
            duration = AudioDurationReader.readM4a(bytes);
            contentType = "audio/mp4";
        } else if (extension.equals("webm") && isWebm(bytes)) {
            // 브라우저 녹음 webm에는 길이 정보가 없는 경우가 많다
            duration = Optional.empty();
            contentType = "audio/webm";
        } else {
            log.warn("[uploadAudio] 지원하지 않는 음성 형식: {}", extension);
            throw new FileNotSupportedException();
        }

        // 길이를 읽었으면 60초로 막고, 읽지 못했으면(또는 0이면) 크기로 막는다. 60초 녹음 제한은 프론트도 건다.
        boolean timed = duration.isPresent() && !duration.get().isZero();
        if (timed ? duration.get().compareTo(MAX_AUDIO_DURATION) > 0 : bytes.length > MAX_UNTIMED_AUDIO_SIZE) {
            log.warn("[uploadAudio] 음성 길이 제한 위반: {}, {}바이트",
                    duration.map(Duration::toString).orElse("알 수 없음"), bytes.length);
            throw new FileNotSupportedException();
        }

        return new AudioData(bytes, extension, contentType);
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

    private boolean isWav(byte[] bytes) {
        return bytes.length >= 12 && startsWith(bytes, 0, "RIFF") && startsWith(bytes, 8, "WAVE");
    }

    // MP4 계열은 첫 박스가 ftyp다
    private boolean isMp4(byte[] bytes) {
        return bytes.length >= 8 && startsWith(bytes, 4, "ftyp");
    }

    // WebM(Matroska)은 EBML 헤더 1A 45 DF A3로 시작한다
    private boolean isWebm(byte[] bytes) {
        return bytes.length >= 4
                && (bytes[0] & 0xFF) == 0x1A && (bytes[1] & 0xFF) == 0x45
                && (bytes[2] & 0xFF) == 0xDF && (bytes[3] & 0xFF) == 0xA3;
    }

    private boolean startsWith(byte[] bytes, int offset, String ascii) {
        for (int i = 0; i < ascii.length(); i++) {
            if (bytes[offset + i] != ascii.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private boolean isPng(byte[] bytes) {
        return bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G'
                && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A;
    }
}
