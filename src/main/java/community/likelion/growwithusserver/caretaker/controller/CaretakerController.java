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
    private final community.likelion.growwithusserver.caretaker.service.AvatarService avatars;
    @PostMapping("/profile")
    public GlobalResponse<CaretakerDtos.Profile> register(@Valid @RequestBody CaretakerDtos.Register request) {
        return service.register(SecurityUtil.getUsername(), request);
    }
    // 사진과 동의 값이 빠져도 서비스가 C4003, C4001로 구분해 돌려주도록 필수로 두지 않는다
    @PostMapping(value = "/avatar", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public GlobalResponse<CaretakerDtos.Avatar> requestAvatar(
            @RequestPart(value = "photo", required = false) org.springframework.web.multipart.MultipartFile photo,
            @RequestParam(value = "portraitConsent", required = false) Boolean portraitConsent) {
        return avatars.request(SecurityUtil.getUsername(), photo, portraitConsent);
    }
    @GetMapping("/avatar")
    public GlobalResponse<CaretakerDtos.Avatar> avatar() {
        return avatars.get(SecurityUtil.getUsername());
    }
    @GetMapping("/summary")
    public GlobalResponse<CaretakerDtos.Summary> summary(@RequestParam(required = false) String month) {
        return service.summary(SecurityUtil.getUsername(), month);
    }
}
