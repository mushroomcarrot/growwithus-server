package community.likelion.growwithusserver.common.id;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// API에 노출하는 ID 형식. DB PK(Long)에 접두어를 붙인 문자열이다 (예: usr_12).
@Getter
@RequiredArgsConstructor
public enum IdPrefix {
    USER("usr"),
    GARDEN("gdn"),
    FARM("farm"),
    CROP("crop"),
    CARETAKER("crt"),
    TASK("tsk"),
    CARE_LOG("cl"),
    VOICE_MEMO("vm"),
    PHOTO("pht"),
    EVENT("evt"),
    DIARY("dry"),
    VISIT("vst"),
    NOTIFICATION("ntf"),
    LEAGUE("lg"),
    QUEST("qst"),
    QUIZ("qz");

    private static final String SEPARATOR = "_";

    private final String value;

    public String format(Long id) {
        return id == null ? null : value + SEPARATOR + id;
    }

    // 외부 ID를 PK로 바꾼다. 접두어가 다르거나 숫자가 아니면 C002.
    public Long parse(String publicId) {
        String head = value + SEPARATOR;
        if (publicId == null || !publicId.startsWith(head)) {
            throw new InvalidIdException();
        }

        try {
            long id = Long.parseLong(publicId.substring(head.length()));
            if (id <= 0) {
                throw new InvalidIdException();
            }
            return id;
        } catch (NumberFormatException e) {
            throw new InvalidIdException();
        }
    }
}
