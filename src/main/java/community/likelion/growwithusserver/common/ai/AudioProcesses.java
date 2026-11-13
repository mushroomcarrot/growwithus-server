package community.likelion.growwithusserver.common.ai;

import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;

final class AudioProcesses {
    private static final Semaphore SLOT = new Semaphore(1);
    private AudioProcesses() {}
    static int run(List<String> command) {
        if (!SLOT.tryAcquire()) throw new AiClientException();
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(20, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new AiClientException(); }
            return process.exitValue();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); throw new AiClientException();
        } catch (java.io.IOException ex) {
            throw new AiClientException();
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            SLOT.release();
        }
    }
    static void cleanup(Path... paths) {
        for (Path path : paths) if (path != null) {
            try { Files.deleteIfExists(path); } catch (java.io.IOException ignored) { }
        }
    }
}
