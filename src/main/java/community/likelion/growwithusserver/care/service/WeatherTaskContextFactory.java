package community.likelion.growwithusserver.care.service;

import community.likelion.growwithusserver.ai.logic.TaskRules;
import community.likelion.growwithusserver.care.data.enums.TaskStatus;
import community.likelion.growwithusserver.care.data.repository.TaskRepository;
import community.likelion.growwithusserver.care.garden.CareGardenReader.Garden;
import community.likelion.growwithusserver.voice.service.VoiceMemoStore;
import community.likelion.growwithusserver.weather.service.WeatherService;
import community.likelion.growwithusserver.growth.service.GrowthStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Service @RequiredArgsConstructor
public class WeatherTaskContextFactory implements TaskContextFactory {
    private final TaskRepository tasks;
    private final WeatherService weather;
    private final VoiceMemoStore voiceMemos;
    private final GrowthStatusService growth;

    public TaskRules.Context create(Garden garden, LocalDate date) {
        var previous = tasks.findAllByGardenIdAndDateBetweenOrderByDateDesc(garden.gardenId(), date.minusDays(14), date);
        var history = previous.stream().filter(t -> t.getStatus() == TaskStatus.DONE && t.getCompletedAt() != null)
                .map(t -> new TaskRules.History(t.getType(), t.getCompletedAt().toLocalDate())).toList();
        var ids = previous.stream().filter(t -> t.getDate().isBefore(date) && !t.getDate().isBefore(date.minusDays(7)))
                .map(t -> t.getId()).toList();
        var current = weather.getCurrentWeather(garden.nx(), garden.ny());
        boolean known = current.isPresent() && current.get().getUpdatedAt() != null
                && !current.get().getUpdatedAt().isBefore(OffsetDateTime.now().minusHours(24));
        var stage = growth.stage(garden);
        return new TaskRules.Context(date, stage, known, weather.isRainExpected(garden.nx(), garden.ny(), date),
                garden.guidance(), history, voiceMemos.recentIssuesForTasks(ids, date.minusDays(7)));
    }
}
