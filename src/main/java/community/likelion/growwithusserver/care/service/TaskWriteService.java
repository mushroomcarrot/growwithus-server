package community.likelion.growwithusserver.care.service;

import community.likelion.growwithusserver.care.data.dto.TaskDtos;
import community.likelion.growwithusserver.care.data.entity.*;
import community.likelion.growwithusserver.care.data.enums.TaskStatus;
import community.likelion.growwithusserver.care.data.repository.*;
import community.likelion.growwithusserver.common.ai.VisionClient;
import community.likelion.growwithusserver.common.id.IdPrefix;
import community.likelion.growwithusserver.common.response.ErrorCode;
import community.likelion.growwithusserver.voice.data.exception.VoiceMemoException;
import community.likelion.growwithusserver.voice.service.VoiceMemoStore;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service @RequiredArgsConstructor
public class TaskWriteService {
    private final TaskAccess access;
    private final TaskRepository tasks;
    private final TaskPhotoRepository photos;
    private final CareLogRepository logs;
    private final VoiceMemoStore voice;
    private final TaskVisibility visibility;
    private final ApplicationEventPublisher events;
    private final community.likelion.growwithusserver.growth.service.GrowthStatusService growth;

    @Transactional
    public TaskDtos.Photo savePhoto(String username, Long taskId, String url, VisionClient.Check check) {
        access.open(username, taskId, true);
        TaskPhoto photo = photos.save(TaskPhoto.builder().taskId(taskId).caretakerUsername(username).url(url)
                .aiMatched(check == null ? null : check.matched()).aiMessage(check == null ? null : check.message())
                .createdAt(LocalDateTime.now()).build());
        return TaskDtos.Photo.builder().photoId(IdPrefix.PHOTO.format(photo.getId())).photoUrl(photo.getUrl())
                .aiCheck(check == null ? null : TaskDtos.AiCheck.builder().matched(check.matched()).message(check.message()).build()).build();
    }

    @Transactional
    public TaskDtos.Completed complete(String username, Long taskId, TaskDtos.CompleteRequest request) {
        Task task = access.open(username, taskId, true);
        var garden = access.garden(username, task.getGardenId(), true);
        // 날씨 또는 성장 단계가 바뀌어 화면에서 빠진 작업은 오래된 카드로 완료하지 않는다.
        if (visibility.select(username, java.util.List.of(task), task.getDate()).tasks().isEmpty()) {
            throw new VoiceMemoException(ErrorCode.INVALID_PARAMETER);
        }
        String photoUrl = null;
        if (request != null && request.getPhotoId() != null) {
            TaskPhoto photo = photos.findById(IdPrefix.PHOTO.parse(request.getPhotoId()))
                    .orElseThrow(() -> new VoiceMemoException(ErrorCode.DATA_NOT_FOUND));
            if (!taskId.equals(photo.getTaskId()) || !username.equals(photo.getCaretakerUsername())) {
                throw new VoiceMemoException(ErrorCode.ACCESS_DENIED);
            }
            photoUrl = photo.getUrl();
        }
        Long memoId = request == null || request.getVoiceMemoId() == null ? null : IdPrefix.VOICE_MEMO.parse(request.getVoiceMemoId());
        var memo = memoId == null ? null : voice.getConfirmedForTask(username, taskId, memoId);
        String summary = memo == null ? task.getTitle() + " 작업을 마쳤어요." : memo.summary();
        CareLog log = logs.save(CareLog.builder().gardenId(task.getGardenId()).taskId(taskId)
                .caretakerUsername(username).ownerUsername(garden.ownerUsername()).photoUrl(photoUrl).voiceMemoId(memoId)
                .transcript(memo == null ? null : memo.transcript()).summary(summary).createdAt(LocalDateTime.now()).build());
        task.setStatus(TaskStatus.DONE); task.setCompletedAt(LocalDateTime.now()); task.setCareLogId(log.getId());
        events.publishEvent(new CareCompleted(garden.ownerUsername(), summary, IdPrefix.CARE_LOG.format(log.getId())));
        long remaining = visibility.select(username, tasks.findAllByCaretakerUsernameAndDateOrderByIdAsc(username, task.getDate()), task.getDate())
                .tasks().stream().filter(t -> t.getStatus() == TaskStatus.TODO).count();
        var expectedStage = growth.expectedCheck(garden);
        return TaskDtos.Completed.builder().taskId(IdPrefix.TASK.format(taskId)).status(TaskStatus.DONE)
                .careLogId(IdPrefix.CARE_LOG.format(log.getId())).remainingTasks(remaining)
                .stageCheck(TaskDtos.StageCheck.builder().required(expectedStage != null).expectedStage(expectedStage)
                        .gardenId(IdPrefix.GARDEN.format(garden.gardenId())).build()).build();
    }
}
