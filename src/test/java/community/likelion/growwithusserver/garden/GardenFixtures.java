package community.likelion.growwithusserver.garden;

import community.likelion.growwithusserver.farm.data.entity.Crop;
import community.likelion.growwithusserver.farm.data.entity.Farm;
import community.likelion.growwithusserver.farm.data.entity.Plot;
import community.likelion.growwithusserver.farm.data.enums.District;
import community.likelion.growwithusserver.farm.data.enums.GrowthStage;
import community.likelion.growwithusserver.garden.data.entity.Garden;
import community.likelion.growwithusserver.garden.data.enums.CharacterMood;
import community.likelion.growwithusserver.user.data.entity.User;
import community.likelion.growwithusserver.user.data.enums.UserRole;

import java.time.LocalDate;

// 텃밭 관련 단위 테스트가 함께 쓰는 시연 텃밭 (민지네, 토순이, B-14)
public final class GardenFixtures {
    private GardenFixtures() {
    }

    public static User owner() {
        return User.builder().id(1L).username("minji").name("김민지").role(UserRole.OWNER).build();
    }

    public static User caretaker() {
        return User.builder().id(2L).username("soonja").name("박순자").role(UserRole.CARETAKER).build();
    }

    public static Garden garden() {
        Farm farm = Farm.builder().id(1L).name("처인 테마 주말농장").district(District.CHEOIN)
                .latitude(37.2342).longitude(127.2017).nx(64).ny(119).build();
        Crop crop = Crop.builder().id(1L).name("방울토마토").characterName("토순이")
                .baseTemp(10).sproutGdd(150).flowerGdd(550).fruitGdd(1000).build();
        User caretaker = caretaker();
        Plot plot = Plot.builder().id(5L).farm(farm).plotCode("B-14").crop(crop).caretaker(caretaker).assigned(true).build();

        return Garden.builder()
                .id(3L)
                .owner(owner())
                .plot(plot)
                .crop(crop)
                .caretaker(caretaker)
                .characterName("토순이")
                .mood(CharacterMood.HAPPY)
                .startedOn(LocalDate.now().minusDays(10))
                .stage(GrowthStage.FLOWER)
                .totalXp(1240)
                .build();
    }
}
