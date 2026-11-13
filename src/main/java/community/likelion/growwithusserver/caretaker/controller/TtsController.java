package community.likelion.growwithusserver.caretaker.controller;

import community.likelion.growwithusserver.caretaker.service.TtsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
public class TtsController {
    private final TtsService tts;
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Request {
        @NotBlank @Size(max = 300) private String text;
        @DecimalMin("0.5") @DecimalMax("1.5") private Double speed;
    }
    @PostMapping("/tts")
    public ResponseEntity<byte[]> synthesize(@Valid @RequestBody Request request) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("audio/mpeg"))
                .header(HttpHeaders.CACHE_CONTROL, "no-store").body(tts.synthesize(request.getText(), request.getSpeed()));
    }
}
