package community.likelion.growwithusserver.diary.service;

import community.likelion.growwithusserver.care.service.CareCompleted;
import community.likelion.growwithusserver.care.data.repository.*;
import community.likelion.growwithusserver.caretaker.data.repository.CaretakerProfileRepository;
import community.likelion.growwithusserver.common.id.IdPrefix;
import community.likelion.growwithusserver.diary.data.entity.DiaryEntry;
import community.likelion.growwithusserver.diary.data.repository.DiaryRepository;
import community.likelion.growwithusserver.growth.service.GrowthChanged;
import community.likelion.growwithusserver.growth.data.repository.GrowthEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component @RequiredArgsConstructor
public class DiaryListener {
    private final DiaryRepository entries;
    private final CareLogRepository logs;
    private final TaskRepository tasks;
    private final CaretakerProfileRepository caretakers;
    private final GrowthEventRepository growthEvents;

    // 같은 트랜잭션에서 저장하므로 일기 저장 실패도 원래 작업과 함께 롤백한다.
    @EventListener @Transactional(propagation = Propagation.MANDATORY)
    public void onCare(CareCompleted event) {
        var log = logs.findById(IdPrefix.CARE_LOG.parse(event.careLogId())).orElseThrow();
        var task = tasks.findById(log.getTaskId()).orElseThrow();
        var name = caretakers.findByUsername(log.getCaretakerUsername()).map(p -> p.getName()).orElse("관리사");
        entries.save(DiaryEntry.builder().gardenId(log.getGardenId()).type(DiaryEntry.Type.CARE_LOG).sourceId(log.getId())
                .taskTypes(task.getType().name()).transcript(log.getTranscript()).summary(log.getSummary())
                .photoUrl(log.getPhotoUrl()).caretakerName(name).createdAt(log.getCreatedAt()).build());
    }

    @EventListener @Transactional(propagation = Propagation.MANDATORY)
    public void onGrowth(GrowthChanged event) {
        var growth = growthEvents.findById(IdPrefix.EVENT.parse(event.eventId())).orElseThrow();
        entries.save(DiaryEntry.builder().gardenId(growth.getGardenId()).type(DiaryEntry.Type.STAGE_CHANGE).sourceId(growth.getId())
                .fromStage(growth.getFromStage()).toStage(growth.getToStage()).summary(growth.getSummary()).createdAt(growth.getCreatedAt()).build());
    }
}
