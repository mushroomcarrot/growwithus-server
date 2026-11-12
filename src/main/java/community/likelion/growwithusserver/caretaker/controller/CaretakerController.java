package community.likelion.growwithusserver.caretaker.controller;
import community.likelion.growwithusserver.caretaker.data.dto.CaretakerDtos;
import community.likelion.growwithusserver.caretaker.service.CaretakerService;
import community.likelion.growwithusserver.common.response.GlobalResponse;
import community.likelion.growwithusserver.common.security.SecurityUtil;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/caretakers/me") @RequiredArgsConstructor
public class CaretakerController {
    private final CaretakerService service;
    @PostMapping("/profile")
    public GlobalResponse<CaretakerDtos.Profile> register(@Valid @RequestBody CaretakerDtos.Register request) {
        return service.register(SecurityUtil.getUsername(), request);
    }
    @GetMapping("/summary")
    public GlobalResponse<CaretakerDtos.Summary> summary(@RequestParam(required = false) String month) {
        return service.summary(SecurityUtil.getUsername(), month);
    }
}
