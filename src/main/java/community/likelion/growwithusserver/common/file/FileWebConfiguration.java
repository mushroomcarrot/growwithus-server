package community.likelion.growwithusserver.common.file;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

// 로컬 저장소에 올린 파일을 /uploads/**로 내려준다
@Configuration
@ConditionalOnProperty(name = "file.storage", havingValue = "local", matchIfMissing = true)
public class FileWebConfiguration implements WebMvcConfigurer {
    private final String location;

    public FileWebConfiguration(@Value("${file.upload-dir}") String uploadDir) {
        this.location = Path.of(uploadDir).toAbsolutePath().normalize().toUri().toString();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(LocalFileStorage.URL_PATH + "/**")
                .addResourceLocations(location.endsWith("/") ? location : location + "/");
    }
}
