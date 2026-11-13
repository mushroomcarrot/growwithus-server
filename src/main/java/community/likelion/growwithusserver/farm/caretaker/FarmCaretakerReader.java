package community.likelion.growwithusserver.farm.caretaker;

import java.util.Optional;

// 관리사 프로필 도메인과의 접점. 관리사 프로필 도메인이 생기면 구현체를 @Component로 등록한다.
// 그러면 기본 빈(FarmCaretakerConfiguration)은 빠진다.
public interface FarmCaretakerReader {

    // 관리사의 로그인 아이디로 프로필을 찾는다. 프로필이 없으면 비어 있다
    Optional<FarmCaretaker> findByUsername(String username);
}
