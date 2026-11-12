package community.likelion.growwithusserver.care.service;

import community.likelion.growwithusserver.care.data.repository.TaskRepository;
import community.likelion.growwithusserver.care.data.enums.TaskStatus;
import community.likelion.growwithusserver.care.garden.CareGardenReader;
import community.likelion.growwithusserver.voice.task.VoiceTaskReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Optional;

@Component @RequiredArgsConstructor
public class TaskVoiceReader implements VoiceTaskReader {
    private final TaskRepository tasks;
    private final CareGardenReader gardens;
    @Override public Optional<VoiceTask> findTask(Long id) { return read(id, false); }
    @Override public Optional<VoiceTask> findTaskForUpdate(Long id) { return read(id, true); }
    private Optional<VoiceTask> read(Long id, boolean lock) {
        return (lock ? tasks.findForUpdate(id) : tasks.findById(id)).flatMap(task ->
                (lock ? gardens.findGardenForUpdate(task.getGardenId()) : gardens.findGarden(task.getGardenId()))
                .filter(g -> task.getGardenId().equals(g.gardenId()) && task.getCaretakerUsername().equals(g.caretakerUsername()))
                .map(g -> new VoiceTask(task.getId(), g.caretakerUsername(), task.getStatus() != TaskStatus.TODO)));
    }
}
