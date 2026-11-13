package community.likelion.growwithusserver.demo.service;

import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.demo.data.dto.DemoStateResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DemoResetService {
    private final DemoTaskResetService tasks;
    private final DemoSimulationService simulation;
    private final DemoService demo;

    @Transactional
    public GlobalResponse<DemoStateResponseDto> reset() {
        tasks.archiveToday();
        simulation.resetGrowth();
        return demo.reset();
    }
}
