package community.likelion.growwithusserver.common.ai;

import java.net.URI;
import java.util.Set;

final class LocalAiAddress {
    private LocalAiAddress() {}

    static void validate(String address) {
        if (address == null) throw new IllegalArgumentException("로컬 AI 주소가 필요합니다.");
        URI uri = URI.create(address);
        if (!"http".equals(uri.getScheme()) || uri.getHost() == null
                || !Set.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost())
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) {
            throw new IllegalArgumentException("로컬 AI 주소는 HTTP loopback 주소만 허용합니다.");
        }
    }
}
