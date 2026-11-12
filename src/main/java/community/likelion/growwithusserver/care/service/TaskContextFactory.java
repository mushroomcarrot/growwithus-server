package community.likelion.growwithusserver.care.service;
import community.likelion.growwithusserver.ai.logic.TaskRules;
import community.likelion.growwithusserver.care.garden.CareGardenReader.Garden;
import java.time.LocalDate;
public interface TaskContextFactory { TaskRules.Context create(Garden garden, LocalDate date); }
