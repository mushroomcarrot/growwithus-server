package community.likelion.growwithusserver.demo;

import community.likelion.growwithusserver.care.data.entity.*;
import community.likelion.growwithusserver.care.data.repository.*;
import community.likelion.growwithusserver.demo.service.DemoTaskResetService;
import community.likelion.growwithusserver.garden.data.entity.Garden;
import community.likelion.growwithusserver.garden.data.repository.GardenRepository;
import community.likelion.growwithusserver.farm.data.entity.*;
import community.likelion.growwithusserver.user.data.entity.User;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DemoTaskResetServiceTest {
    @Test void 시연_작업만_보관하고_모든_작업을_먼저_잠근다() {
        var days = mock(TaskDayRepository.class);
        var tasks = mock(TaskRepository.class);
        var gardens = mock(GardenRepository.class);
        when(days.lockDay("soonja", LocalDate.now())).thenReturn(Optional.of(new TaskDay()));
        var first = Task.builder().id(1L).gardenId(10L).build();
        var second = Task.builder().id(2L).gardenId(10L).build();
        var other = Task.builder().id(3L).gardenId(20L).build();
        when(tasks.findAllByCaretakerUsernameAndDateOrderByIdAsc("soonja", LocalDate.now()))
                .thenReturn(List.of(first, second, other));
        for (var task : List.of(first, second, other)) when(tasks.findForUpdate(task.getId())).thenReturn(Optional.of(task));
        var care = User.builder().username("soonja").build();
        var owner = User.builder().username("minji").build();
        when(gardens.findByIdForUpdate(10L)).thenReturn(Optional.of(Garden.builder().owner(owner).caretaker(care)
                .plot(Plot.builder().plotCode("B-14").farm(Farm.builder().externalId("demo:theme").build()).build()).build()));
        when(gardens.findByIdForUpdate(20L)).thenReturn(Optional.of(Garden.builder().owner(owner).caretaker(care)
                .plot(Plot.builder().plotCode("B-14").farm(Farm.builder().externalId("actual").build()).build()).build()));
        new DemoTaskResetService(days, tasks, gardens).archiveToday();
        assertThat(first.getArchiveNumber()).isEqualTo(1L);
        assertThat(second.getArchiveNumber()).isEqualTo(2L);
        assertThat(other.getArchiveNumber()).isZero();
        var order = inOrder(tasks, gardens);
        order.verify(tasks).findAllByCaretakerUsernameAndDateOrderByIdAsc("soonja", LocalDate.now());
        order.verify(tasks).findForUpdate(1L);
        order.verify(tasks).findForUpdate(2L);
        order.verify(tasks).findForUpdate(3L);
        order.verify(gardens, times(2)).findByIdForUpdate(10L);
        verify(tasks, never()).delete(any());
    }
}
