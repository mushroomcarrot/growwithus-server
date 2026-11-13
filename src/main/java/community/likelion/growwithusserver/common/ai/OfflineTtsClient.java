package community.likelion.growwithusserver.common.ai;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** 큰 모델이나 별도 작업자 없이 설치된 eSpeak NG와 FFmpeg로 읽어준다. */
public final class OfflineTtsClient implements TtsClient {
    @Override public byte[] synthesize(String text) { return synthesize(text, 0.9); }
    @Override public byte[] synthesize(String text, double speed) {
        if (text == null || text.isBlank() || text.length() > 300 || text.indexOf('\0') >= 0
                || !Double.isFinite(speed) || speed < 0.5 || speed > 1.5) throw new AiClientException();
        Path source = null, wave = null, mp3 = null;
        try {
            source = Files.createTempFile("grow-tts-", ".txt"); wave = Files.createTempFile("grow-tts-", ".wav");
            mp3 = Files.createTempFile("grow-tts-", ".mp3"); Files.writeString(source, text, StandardCharsets.UTF_8);
            if (AudioProcesses.run(List.of("espeak-ng", "-v", "ko", "-s", String.valueOf(Math.round(175 * speed)),
                    "-f", source.toString(), "-w", wave.toString())) != 0) throw new AiClientException();
            if (AudioProcesses.run(List.of("ffmpeg", "-y", "-nostdin", "-v", "error", "-i", wave.toString(),
                    "-t", "120", "-codec:a", "libmp3lame", "-b:a", "48k", mp3.toString())) != 0) throw new AiClientException();
            long size = Files.size(mp3);
            if (size == 0 || size > 1024 * 1024) throw new AiClientException();
            return Files.readAllBytes(mp3);
        } catch (java.io.IOException ex) { throw new AiClientException(); }
        finally { AudioProcesses.cleanup(source, wave, mp3); }
    }
}
