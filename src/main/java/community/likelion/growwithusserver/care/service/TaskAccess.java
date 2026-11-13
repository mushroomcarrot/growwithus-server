package community.likelion.growwithusserver.care.service;

import community.likelion.growwithusserver.care.data.entity.Task;
import community.likelion.growwithusserver.care.data.enums.TaskStatus;
import community.likelion.growwithusserver.care.data.repository.TaskRepository;
import community.likelion.growwithusserver.care.garden.CareGardenReader;
import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.voice.data.exception.VoiceMemoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class TaskAccess {
    private final TaskRepository tasks;
    private final CareGardenReader gardens;

    public Task owned(String username, Long id, boolean lock) {
        Task task = (lock ? tasks.findForUpdate(id) : tasks.findById(id))
                .orElseThrow(() -> new VoiceMemoException(ErrorCode.DATA_NOT_FOUND));
        var garden = garden(username, task.getGardenId(), lock);
        if (username == null || !username.equals(task.getCaretakerUsername())
                || !username.equals(garden.caretakerUsername())) throw new VoiceMemoException(ErrorCode.ACCESS_DENIED);
        return task;
    }

    public CareGardenReader.Garden garden(String username, Long id, boolean lock) {
        var garden = (lock ? gardens.findGardenForUpdate(id) : gardens.findGarden(id))
                .orElseThrow(() -> new VoiceMemoException(ErrorCode.DATA_NOT_FOUND));
        if (!id.equals(garden.gardenId()) || username == null || !username.equals(garden.caretakerUsername())) {
            throw new VoiceMemoException(ErrorCode.ACCESS_DENIED);
        }
        return garden;
    }

    public Task open(String username, Long id, boolean lock) {
        Task task = owned(username, id, lock);
        if (task.getArchiveNumber() != 0 || task.getStatus() != TaskStatus.TODO) throw new VoiceMemoException(ErrorCode.ALREADY_PROCESSED);
        return task;
    }
}
