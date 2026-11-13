package community.likelion.growwithusserver.common.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class NativeAudioTest {
    @TempDir Path temp;
    private void installed() {
        assumeTrue(Files.isExecutable(Path.of("/usr/bin/ffmpeg")) && Files.isExecutable(Path.of("/usr/bin/espeak-ng")),
                "Native audio tests run in the Linux Docker build image");
    }
    @Test void normalizesWavWebmMp4AndM4aUsingRealDecoder() throws Exception {
        installed();
        Path wave = temp.resolve("source.wav"); Files.write(wave, FfmpegAudioNormalizer.wave(new byte[32000]));
        var normalizer = new FfmpegAudioNormalizer();
        assertEquals(32044, normalizer.normalize(Files.readAllBytes(wave)).length);
        for (String suffix : List.of("webm", "mp4", "m4a")) {
            Path encoded = temp.resolve("audio." + suffix);
            assertEquals(0, AudioProcesses.run(List.of("ffmpeg", "-y", "-nostdin", "-v", "error", "-i", wave.toString(), encoded.toString())));
            byte[] decoded = normalizer.normalize(Files.readAllBytes(encoded));
            assertEquals("RIFF", new String(decoded, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
            assertTrue(decoded.length > 16000 && decoded.length < 64044);
        }
    }
    @Test void rejectsCorruptAudioAndOverSixtySeconds() {
        installed(); var normalizer = new FfmpegAudioNormalizer();
        assertThrows(community.likelion.growwithusserver.common.file.exception.FileNotSupportedException.class,
                () -> normalizer.normalize(new byte[]{1, 2, 3}));
        assertThrows(community.likelion.growwithusserver.common.file.exception.FileNotSupportedException.class,
                () -> normalizer.normalize(FfmpegAudioNormalizer.wave(new byte[61 * 32000])));
    }
    @Test void synthesizesKoreanIntoPlayableMp3WithoutModelOrNetwork() throws Exception {
        installed(); byte[] mp3 = new OfflineTtsClient().synthesize("오늘 텃밭에 물을 줘요.", 0.9);
        assertTrue(mp3.length > 100); Path source = temp.resolve("speech.mp3"); Files.write(source, mp3);
        assertEquals(0, AudioProcesses.run(List.of("ffmpeg", "-nostdin", "-v", "error", "-i", source.toString(), "-f", "null", "-")));
    }
}
