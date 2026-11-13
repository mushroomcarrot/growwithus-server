package community.likelion.growwithusserver.garden;

import community.likelion.growwithusserver.care.garden.CareGardenReader;
import community.likelion.growwithusserver.diary.service.DiaryService;
import community.likelion.growwithusserver.garden.service.GardenCareReader;
import community.likelion.growwithusserver.garden.service.GardenGrowthProjection;
import community.likelion.growwithusserver.growth.service.GrowthProjection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Profiles;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

// Uses the local verification database, with actual domain beans and no demo seeding.
@SpringBootTest(properties = {"spring.profiles.active=local", "demo.enabled=false", "scheduler.enabled=false"})
@ActiveProfiles("local")
class AiDomainContextTest {
    @Autowired ApplicationContext context;
    @Test void selectsRealDomainAdaptersAndOneDiaryEndpoint() {
        assertFalse(context.getEnvironment().acceptsProfiles(Profiles.of("local-demo")));
        assertTrue(GardenCareReader.class.isAssignableFrom(org.springframework.aop.support.AopUtils.getTargetClass(context.getBean(CareGardenReader.class))));
        assertTrue(GardenGrowthProjection.class.isAssignableFrom(org.springframework.aop.support.AopUtils.getTargetClass(context.getBean(GrowthProjection.class))));
        assertNotNull(context.getBean(DiaryService.class));
        assertFalse(context.containsBean("localSnapshotDiaryController"));
        assertTrue(context.containsBean("diaryController"));
    }
}
