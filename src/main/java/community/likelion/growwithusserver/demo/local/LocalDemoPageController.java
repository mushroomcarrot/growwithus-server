package community.likelion.growwithusserver.demo.local;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @Profile("local-demo")
public class LocalDemoPageController {
    @GetMapping("/local-demo/status")
    public community.likelion.growwithusserver.common.response.GlobalResponse<java.util.Map<String, Boolean>> status() {
        return community.likelion.growwithusserver.common.response.GlobalResponse.ok(java.util.Map.of("localDemo", true, "simulatedAgronomy", true));
    }
    @GetMapping(value = {"/local-demo", "/local-demo/"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> page() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ClassPathResource("local-demo/index.html"));
    }
    @GetMapping(value = "/local-demo/app.js", produces = "text/javascript")
    public ResponseEntity<Resource> script() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ClassPathResource("local-demo/app.js"));
    }
}
