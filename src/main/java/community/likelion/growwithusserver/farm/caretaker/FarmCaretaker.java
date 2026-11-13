package community.likelion.growwithusserver.farm.caretaker;

/**
 * 농장, 텃밭 화면이 관리사 프로필에서 필요로 하는 값.
 *
 * @param caretakerId 관리사 프로필 PK (외부 ID crt_3의 숫자)
 * @param name        관리사 프로필의 성함
 * @param avatarUrl   아바타 이미지 주소. 없으면 null
 */
public record FarmCaretaker(Long caretakerId, String name, String avatarUrl) {
}
