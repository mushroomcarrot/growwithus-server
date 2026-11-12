package community.likelion.growwithusserver.common.file;

import community.likelion.growwithusserver.common.file.exception.FileUploadFailedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

// 서버 로컬 디스크에 저장하고 /uploads/** 경로로 내려준다 (FileWebConfiguration)
@Slf4j
@Component
@ConditionalOnProperty(name = "file.storage", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements FileStorage {
    public static final String URL_PATH = "/uploads";

    private final Path root;
    private final String baseUrl;

    public LocalFileStorage(@Value("${file.upload-dir}") String uploadDir,
                            @Value("${file.base-url}") String baseUrl) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @Override
    public byte[] readAudio(String storedUrl) {
        try {
            Path target = audioPath(storedUrl);
            if (!Files.isRegularFile(target, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                    || Files.size(target) > FileUploadService.MAX_AUDIO_SIZE) {
                throw new FileUploadFailedException();
            }
            try (InputStream input = Files.newInputStream(target)) {
                byte[] bytes = input.readNBytes((int) FileUploadService.MAX_AUDIO_SIZE + 1);
                if (bytes.length > FileUploadService.MAX_AUDIO_SIZE) throw new FileUploadFailedException();
                return bytes;
            }
        } catch (IOException ex) {
            throw new FileUploadFailedException();
        }
    }

    @Override
    public void deleteAudio(String storedUrl) {
        try {
            Path target = audioPath(storedUrl);
            if (!Files.exists(target, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return;
            if (!Files.isRegularFile(target, java.nio.file.LinkOption.NOFOLLOW_LINKS)) throw new FileUploadFailedException();
            Files.delete(target);
        } catch (IOException ex) {
            throw new FileUploadFailedException();
        }
    }

    private Path audioPath(String storedUrl) throws IOException {
        String prefix = baseUrl + URL_PATH + "/voice-memos/";
        if (storedUrl == null || !storedUrl.startsWith(prefix)) throw new FileUploadFailedException();
        String filename = storedUrl.substring(prefix.length());
        if (!filename.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.(webm|m4a|mp4|wav)")) {
            throw new FileUploadFailedException();
        }
        Path actualRoot = root.toRealPath();
        Path directory = root.resolve("voice-memos");
        if (Files.isSymbolicLink(directory)) throw new FileUploadFailedException();
        Path parent = directory.toRealPath();
        Path target = parent.resolve(filename);
        if (!parent.startsWith(actualRoot) || Files.isSymbolicLink(target)) throw new FileUploadFailedException();
        return target;
    }

    @Override
    public String store(String directory, String filename, InputStream content, long size, String contentType) {
        Path target = root.resolve(directory).resolve(filename).normalize();
        if (!target.startsWith(root)) {
            log.error("[store] 저장소 밖 경로로 저장 시도: {}/{}", directory, filename);
            throw new FileUploadFailedException();
        }

        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("[store] 파일 저장 실패: {}", target, e);
            throw new FileUploadFailedException();
        }

        String relativePath = root.relativize(target).toString().replace('\\', '/');
        return baseUrl + URL_PATH + "/" + relativePath;
    }
}
