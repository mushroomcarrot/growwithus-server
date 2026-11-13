package community.likelion.growwithusserver.game.service;

import community.likelion.growwithusserver.garden.data.entity.Garden;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

// 주간 XP로 순위를 매긴다. XP가 같으면 같은 순위이고 다음 순위는 그만큼 건너뛴다 (1, 1, 3).
public final class LeagueRanking {
    private LeagueRanking() {
    }

    public record Standing(Garden garden, int weeklyXp, int rank) {
    }

    /**
     * @param weeklyXpByGarden 텃밭 PK별 주간 XP. 없는 텃밭은 0으로 본다
     * @return 순위순. 같은 순위 안에서는 먼저 만든 텃밭이 앞이다
     */
    public static List<Standing> rank(List<Garden> gardens, Map<Long, Integer> weeklyXpByGarden) {
        List<Garden> sorted = gardens.stream()
                .sorted(Comparator.comparingInt((Garden garden) -> weeklyXpByGarden.getOrDefault(garden.getId(), 0)).reversed()
                        .thenComparing(Garden::getId))
                .toList();

        List<Standing> standings = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            int weeklyXp = weeklyXpByGarden.getOrDefault(sorted.get(i).getId(), 0);
            boolean tied = i > 0 && standings.get(i - 1).weeklyXp() == weeklyXp;
            standings.add(new Standing(sorted.get(i), weeklyXp, tied ? standings.get(i - 1).rank() : i + 1));
        }

        return standings;
    }
}
