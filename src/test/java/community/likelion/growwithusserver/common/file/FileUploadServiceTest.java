package community.likelion.growwithusserver.common.file;

import community.likelion.growwithusserver.common.file.exception.FileNotSupportedException;
import community.likelion.growwithusserver.common.file.exception.FileUploadFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileUploadServiceTest {
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};

    @TempDir
    Path uploadDir;

    private FileUploadService fileUploadService;

    @BeforeEach
    void setUp() {
        fileUploadService = new FileUploadService(new LocalFileStorage(uploadDir.toString(), "http://localhost:8080/"));
    }

    @Test
    void readsOriginalStoredAudioForRetry() {
        byte[] audio = {0x1a, 0x45, (byte) 0xdf, (byte) 0xa3, 1};
        String url = fileUploadService.uploadAudio(new MockMultipartFile("audio", "recording.webm", "audio/webm", audio), "voice-memos");
        assertThat(fileUploadService.readStoredAudio(url)).isEqualTo(audio);
    }

    @Test void acceptsTheReproducibleSyntheticKoreanSpeechFixture() throws Exception {
        byte[] bytes;
        try (var input = getClass().getResourceAsStream("/ai-evaluation/synthetic-korean.wav")) {
            assertThat(input).isNotNull(); bytes = input.readAllBytes();
        }
        var duration = AudioDurationReader.readWav(bytes).orElseThrow();
        assertThat(duration.getSeconds()).isBetween(1L, 60L);
        String stored = fileUploadService.uploadAudio(new MockMultipartFile("audio", "synthetic-korean.wav", "audio/wav", bytes), "voice-memos");
        assertThat(fileUploadService.readStoredAudio(stored)).isEqualTo(bytes);
    }

    @Test void deletesOnlyValidatedStoredAudioAndCanRepeatDeletion() throws Exception {
        var storage = new LocalFileStorage(uploadDir.toString(), "http://localhost:8080/");
        byte[] audio = {0x1a, 0x45, (byte) 0xdf, (byte) 0xa3, 1};
        String url = fileUploadService.uploadAudio(new MockMultipartFile("audio", "recording.webm", "audio/webm", audio), "voice-memos");
        Path saved = uploadDir.resolve("voice-memos").resolve(url.substring(url.lastIndexOf('/') + 1));
        storage.deleteAudio(url); assertThat(Files.exists(saved)).isFalse();
        storage.deleteAudio(url);
    }

    @Test void deletingAudioRejectsOtherDirectoriesRemoteUrlsAndTraversal() throws Exception {
        String photo = fileUploadService.uploadImage(new MockMultipartFile("photo", "photo.png", "image/png", PNG), "care-photos");
        var storage = new LocalFileStorage(uploadDir.toString(), "http://localhost:8080");
        String name = "00000000-0000-0000-0000-000000000000.webm";
        for (String url : new String[]{photo, "https://example.test/uploads/voice-memos/" + name,
                "http://localhost:8080/uploads/voice-memos/../" + name, "http://localhost:8080/uploads/voice-memos/" + name + "?x=1"}) {
            assertThatThrownBy(() -> storage.deleteAudio(url)).isInstanceOf(FileUploadFailedException.class);
        }
        assertThat(Files.exists(uploadDir.resolve("care-photos").resolve(photo.substring(photo.lastIndexOf('/') + 1)))).isTrue();
    }

    @Test void audioCleanupNeverDeletesADirectory() throws Exception {
        String filename = "00000000-0000-0000-0000-000000000000.wav";
        Path directory = uploadDir.resolve("voice-memos").resolve(filename);
        Files.createDirectories(directory);
        var storage = new LocalFileStorage(uploadDir.toString(), "http://localhost:8080");
        assertThatThrownBy(() -> storage.deleteAudio("http://localhost:8080/uploads/voice-memos/" + filename))
                .isInstanceOf(FileUploadFailedException.class);
        assertThat(Files.isDirectory(directory)).isTrue();
    }

    @Test
    void rejectsRemoteUrlsOtherDirectoriesAndPathTraversal() {
        String name = "00000000-0000-0000-0000-000000000000.webm";
        for (String url : new String[]{
                "https://example.test/uploads/voice-memos/" + name,
                "http://localhost:8080/uploads/care-photos/" + name,
                "http://localhost:8080/uploads/voice-memos/../" + name,
                "http://localhost:8080/uploads/voice-memos/%2e%2e%2f" + name,
                "http://localhost:8080/uploads/voice-memos/" + name + "?download=1"}) {
            assertThatThrownBy(() -> fileUploadService.readStoredAudio(url)).isInstanceOf(FileUploadFailedException.class);
        }
    }

    @Test
    void rejectsMissingAndOversizedStoredAudio() throws Exception {
        String name = "00000000-0000-0000-0000-000000000000.wav";
        String url = "http://localhost:8080/uploads/voice-memos/" + name;
        assertThatThrownBy(() -> fileUploadService.readStoredAudio(url)).isInstanceOf(FileUploadFailedException.class);
        Files.createDirectories(uploadDir.resolve("voice-memos"));
        try (var file = new java.io.RandomAccessFile(uploadDir.resolve("voice-memos").resolve(name).toFile(), "rw")) {
            file.setLength(FileUploadService.MAX_AUDIO_SIZE + 1);
        }
        assertThatThrownBy(() -> fileUploadService.readStoredAudio(url)).isInstanceOf(FileUploadFailedException.class);
    }

    @Test
    void 사진을_저장하고_URL을_돌려준다() throws Exception {
        String url = fileUploadService.uploadImage(new MockMultipartFile("file", "photo.PNG", "image/png", PNG), "care-photos");

        assertThat(url).startsWith("http://localhost:8080/uploads/care-photos/").endsWith(".png");
        Path saved = uploadDir.resolve("care-photos").resolve(url.substring(url.lastIndexOf('/') + 1));
        assertThat(Files.readAllBytes(saved)).isEqualTo(PNG);

        assertThat(fileUploadService.uploadImage(new MockMultipartFile("file", "a.jpeg", "image/jpeg", JPEG), "care-photos"))
                .endsWith(".jpeg");
    }

    @Test
    void 지원하지_않는_사진은_거부한다() {
        // 확장자만 png이고 내용은 아님
        assertThatThrownBy(() -> fileUploadService.uploadImage(
                new MockMultipartFile("file", "fake.png", "image/png", "hello".getBytes()), "care-photos"))
                .isInstanceOf(FileNotSupportedException.class);
        // 허용하지 않는 확장자
        assertThatThrownBy(() -> fileUploadService.uploadImage(
                new MockMultipartFile("file", "photo.gif", "image/gif", PNG), "care-photos"))
                .isInstanceOf(FileNotSupportedException.class);
        // 빈 파일
        assertThatThrownBy(() -> fileUploadService.uploadImage(
                new MockMultipartFile("file", "photo.png", "image/png", new byte[0]), "care-photos"))
                .isInstanceOf(FileNotSupportedException.class);
        // 10MB 초과
        byte[] large = new byte[(int) FileUploadService.MAX_IMAGE_SIZE + 1];
        System.arraycopy(PNG, 0, large, 0, PNG.length);
        assertThatThrownBy(() -> fileUploadService.uploadImage(
                new MockMultipartFile("file", "photo.png", "image/png", large), "care-photos"))
                .isInstanceOf(FileNotSupportedException.class);
    }

    @Test
    void 음성_길이를_헤더에서_읽는다() {
        assertThat(AudioDurationReader.readWav(wav(30))).contains(Duration.ofSeconds(30));
        assertThat(AudioDurationReader.readM4a(m4a(45, 0))).contains(Duration.ofSeconds(45));
        assertThat(AudioDurationReader.readM4a(m4a(45, 1))).contains(Duration.ofSeconds(45));
        assertThat(AudioDurationReader.readWav("not a wav file".getBytes())).isEmpty();
        assertThat(AudioDurationReader.readM4a("not an m4a file".getBytes())).isEmpty();
    }

    @Test
    void 음성은_60초까지만_받는다() {
        assertThat(fileUploadService.uploadAudio(new MockMultipartFile("file", "memo.wav", "audio/wav", wav(60)), "voice-memos"))
                .endsWith(".wav");
        assertThat(fileUploadService.uploadAudio(new MockMultipartFile("file", "memo.m4a", "audio/mp4", m4a(10, 0)), "voice-memos"))
                .endsWith(".m4a");

        assertThatThrownBy(() -> fileUploadService.uploadAudio(
                new MockMultipartFile("file", "memo.wav", "audio/wav", wav(61)), "voice-memos"))
                .isInstanceOf(FileNotSupportedException.class);
        assertThatThrownBy(() -> fileUploadService.uploadAudio(
                new MockMultipartFile("file", "memo.m4a", "audio/mp4", m4a(61, 0)), "voice-memos"))
                .isInstanceOf(FileNotSupportedException.class);
        assertThatThrownBy(() -> fileUploadService.uploadAudio(
                new MockMultipartFile("file", "memo.mp3", "audio/mpeg", wav(10)), "voice-memos"))
                .isInstanceOf(FileNotSupportedException.class);
        assertThatThrownBy(() -> fileUploadService.uploadAudio(
                new MockMultipartFile("file", "memo.m4a", "audio/mp4", "broken".getBytes()), "voice-memos"))
                .isInstanceOf(FileNotSupportedException.class);
    }

    @Test
    void 브라우저_녹음_형식도_받는다() {
        // 크롬, 안드로이드: webm (길이 정보 없음)
        assertThat(fileUploadService.uploadAudio(new MockMultipartFile("file", "memo.webm", "audio/webm", webm(1024)), "voice-memos"))
                .endsWith(".webm");
        // 사파리: mp4 확장자, 조각 mp4라 mvhd 길이가 0
        assertThat(fileUploadService.uploadAudio(new MockMultipartFile("file", "memo.mp4", "audio/mp4", m4a(0, 0)), "voice-memos"))
                .endsWith(".mp4");
        assertThat(fileUploadService.uploadAudio(new MockMultipartFile("file", "memo.mp4", "audio/mp4", m4a(30, 0)), "voice-memos"))
                .endsWith(".mp4");
    }

    @Test
    void 길이를_모르는_음성은_크기로_막는다() {
        assertThatThrownBy(() -> fileUploadService.uploadAudio(
                new MockMultipartFile("file", "memo.webm", "audio/webm", webm((int) FileUploadService.MAX_UNTIMED_AUDIO_SIZE + 1)), "voice-memos"))
                .isInstanceOf(FileNotSupportedException.class);
        // 확장자만 webm
        assertThatThrownBy(() -> fileUploadService.uploadAudio(
                new MockMultipartFile("file", "memo.webm", "audio/webm", wav(10)), "voice-memos"))
                .isInstanceOf(FileNotSupportedException.class);
        // 확장자만 wav
        assertThatThrownBy(() -> fileUploadService.uploadAudio(
                new MockMultipartFile("file", "memo.wav", "audio/wav", webm(1024)), "voice-memos"))
                .isInstanceOf(FileNotSupportedException.class);
    }

    // EBML 헤더로 시작하는 webm
    private byte[] webm(int size) {
        byte[] bytes = new byte[size];
        bytes[0] = 0x1A;
        bytes[1] = 0x45;
        bytes[2] = (byte) 0xDF;
        bytes[3] = (byte) 0xA3;
        return bytes;
    }

    // 8kHz, 8bit, mono PCM (초당 8000바이트). LIST 청크를 끼워 넣어 청크 건너뛰기도 확인한다.
    private byte[] wav(int seconds) {
        int byteRate = 8000;
        int dataSize = byteRate * seconds;
        ByteBuffer buffer = ByteBuffer.allocate(12 + 24 + 12 + 8 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(ascii("RIFF")).putInt(buffer.capacity() - 8).put(ascii("WAVE"));
        buffer.put(ascii("fmt ")).putInt(16)
                .putShort((short) 1).putShort((short) 1).putInt(8000).putInt(byteRate).putShort((short) 1).putShort((short) 8);
        buffer.put(ascii("LIST")).putInt(3).put(new byte[]{'a', 'b', 'c', 0});
        buffer.put(ascii("data")).putInt(dataSize);
        return buffer.array();
    }

    // ftyp + moov(mvhd) 박스만 있는 최소 MP4
    private byte[] m4a(int seconds, int version) {
        int timescale = 1000;
        ByteBuffer mvhdBody = ByteBuffer.allocate(version == 1 ? 32 : 20);
        mvhdBody.put((byte) version).put(new byte[3]);
        if (version == 1) {
            mvhdBody.putLong(0).putLong(0).putInt(timescale).putLong((long) seconds * timescale);
        } else {
            mvhdBody.putInt(0).putInt(0).putInt(timescale).putInt(seconds * timescale);
        }

        byte[] mvhd = box("mvhd", mvhdBody.array());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(box("ftyp", ascii("M4A ")));
        out.writeBytes(box("moov", mvhd));
        return out.toByteArray();
    }

    private byte[] box(String type, byte[] body) {
        return ByteBuffer.allocate(8 + body.length).putInt(8 + body.length).put(ascii(type)).put(body).array();
    }

    private byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.ISO_8859_1);
    }
}
