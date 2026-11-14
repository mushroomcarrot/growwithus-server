package community.likelion.growwithusserver.common.ai;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 읽어주기 문장의 구역 번호, 숫자, 단위와 영문 약어를 한국어 발음으로 풀어 쓴다. 화면 문장은 바꾸지 않는다. */
public final class KoreanSpeechText {
    private static final String[] LETTERS = {"에이", "비", "씨", "디", "이", "에프", "지", "에이치", "아이", "제이", "케이", "엘", "엠",
            "엔", "오", "피", "큐", "알", "에스", "티", "유", "브이", "더블유", "엑스", "와이", "지"};
    private static final String[] SINO = {"영", "일", "이", "삼", "사", "오", "육", "칠", "팔", "구"};
    private static final String[] SINO_READ = {"공", "일", "이", "삼", "사", "오", "육", "칠", "팔", "구"};
    private static final String[] NATIVE_ONES = {"", "한", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉"};
    private static final String[] NATIVE_TENS = {"", "열", "스물", "서른", "마흔", "쉰", "예순", "일흔", "여든", "아흔"};
    private static final Map<String, String> UNITS = Map.ofEntries(
            Map.entry("℃", "도"), Map.entry("°C", "도"), Map.entry("°", "도"), Map.entry("%", "퍼센트"),
            Map.entry("mm", "밀리미터"), Map.entry("cm", "센티미터"), Map.entry("km", "킬로미터"), Map.entry("m", "미터"),
            Map.entry("kg", "킬로그램"), Map.entry("g", "그램"), Map.entry("ml", "밀리리터"), Map.entry("mL", "밀리리터"),
            Map.entry("L", "리터"), Map.entry("l", "리터"));

    // B-13은 "비 십삼 구역"으로 읽는다. 뒤에 구역, 칸, 번이 이미 있으면 덧붙이지 않는다.
    private static final Pattern PLOT = Pattern.compile("(?<![A-Za-z0-9])([A-Z])-(\\d{1,3})(?!\\d)(\\s*(?:구역|칸|번))?");
    private static final Pattern CLOCK = Pattern.compile("(?<![\\d:])(\\d{1,2}):(\\d{2})(?![\\d:])");
    private static final Pattern RANGE = Pattern.compile("(\\d)\\s*~\\s*(?=\\d)");
    private static final Pattern BELOW_ZERO = Pattern.compile("(?<![\\dA-Za-z가-힣])-(?=\\d+(?:\\.\\d+)?\\s*(?:도|℃|°))");
    private static final Pattern UNIT = Pattern.compile("(\\d)\\s*(℃|°C|°|%|(?:mm|cm|km|kg|ml|mL|m|g|L|l)(?![A-Za-z]))");
    private static final Pattern NUMBER = Pattern.compile("\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?|\\d+(?:\\.\\d+)?");
    // 고유어로 세는 단위. 개월, 달러, 번호처럼 한자어로 읽는 말은 제외한다.
    private static final Pattern NATIVE_COUNTER = Pattern.compile("^\\s*(?:개(?!월)|명|살|마리|곳|군데|송이|포기|그루|장|잔|병|알|줄|가지"
            + "|시간|달(?!러)|통|컵|뿌리|봉지|차례|바퀴|사람|배|칸|그릇|숟가락|스푼|줌|방울|모금|번(?!지|호))");
    private static final Pattern HOUR = Pattern.compile("^\\s*시(?!간|리즈|즌)");
    private static final Pattern ACRONYM = Pattern.compile("(?<![A-Za-z])[A-Z]{1,5}(?![A-Za-z])");

    private KoreanSpeechText() {}

    public static String normalize(String text) {
        if (text == null || text.isEmpty()) return text;
        String out = replace(PLOT, text, m -> letter(m.group(1).charAt(0)) + " " + sino(Long.parseLong(m.group(2)))
                + (m.group(3) == null ? " 구역" : " " + m.group(3).strip()));
        out = replace(CLOCK, out, m -> {
            int hour = Integer.parseInt(m.group(1)), minute = Integer.parseInt(m.group(2));
            if (hour > 24 || minute > 59) return m.group();
            return (hour >= 1 && hour <= 12 ? nativeNumber(hour) : sino(hour)) + " 시" + (minute == 0 ? "" : " " + sino(minute) + " 분");
        });
        out = RANGE.matcher(out).replaceAll("$1에서 ");
        out = BELOW_ZERO.matcher(out).replaceAll("영하 ");
        out = replace(UNIT, out, m -> m.group(1) + UNITS.get(m.group(2)));
        out = numbers(out);
        out = replace(ACRONYM, out, m -> {
            var spoken = new StringBuilder();
            for (char c : m.group().toCharArray()) spoken.append(letter(c));
            return spoken.toString();
        });
        return out;
    }

    private static String numbers(String text) {
        Matcher m = NUMBER.matcher(text);
        var out = new StringBuilder();
        int last = 0;
        while (m.find()) {
            out.append(text, last, m.start());
            String rest = text.substring(m.end());
            String raw = m.group().replace(",", "");
            int dot = raw.indexOf('.');
            String whole = dot < 0 ? raw : raw.substring(0, dot);
            String spoken;
            if (whole.length() > 15 || (whole.length() > 1 && whole.charAt(0) == '0')) {
                // 전화번호나 0으로 시작하는 번호는 한 자리씩 읽는다.
                spoken = digits(whole, SINO_READ);
            } else {
                long value = Long.parseLong(whole);
                boolean integer = dot < 0;
                if (integer && value == 1 && rest.matches("(?s)^\\s*번째.*")) spoken = "첫";
                else if (integer && value >= 1 && value <= 99 && NATIVE_COUNTER.matcher(rest).find()) spoken = nativeNumber((int) value);
                else if (integer && value >= 1 && value <= 12 && HOUR.matcher(rest).find()) spoken = nativeNumber((int) value);
                else if (integer && value == 6 && rest.startsWith("월")) spoken = "유";
                else if (integer && value == 10 && rest.startsWith("월")) spoken = "시";
                else spoken = sino(value);
            }
            if (dot >= 0) spoken += " 점 " + digits(raw.substring(dot + 1), SINO);
            out.append(spoken);
            // 숫자 뒤에 바로 붙은 한글은 띄어 읽는다. 월, 일, 년은 붙여 읽는다.
            if (!rest.isEmpty() && rest.charAt(0) >= '가' && rest.charAt(0) <= '힣' && "월일년".indexOf(rest.charAt(0)) < 0) out.append(' ');
            last = m.end();
        }
        return out.append(text, last, text.length()).toString();
    }

    private static String replace(Pattern pattern, String text, java.util.function.Function<java.util.regex.MatchResult, String> spoken) {
        return pattern.matcher(text).replaceAll(result -> Matcher.quoteReplacement(spoken.apply(result)));
    }

    private static String letter(char c) { return LETTERS[c - 'A']; }

    private static String digits(String value, String[] names) {
        var out = new StringBuilder();
        for (char c : value.toCharArray()) out.append(out.isEmpty() ? "" : " ").append(names[c - '0']);
        return out.toString();
    }

    private static String nativeNumber(int value) {
        if (value == 20) return "스무";
        return NATIVE_TENS[value / 10] + NATIVE_ONES[value % 10];
    }

    static String sino(long value) {
        if (value == 0) return "영";
        String[] large = {"", "만", "억", "조"};
        String[] small = {"", "십", "백", "천"};
        var out = new StringBuilder();
        for (int group = 3; group >= 0; group--) {
            int part = (int) (value / (long) Math.pow(10000, group) % 10000);
            if (part == 0) continue;
            var words = new StringBuilder();
            for (int place = 3; place >= 0; place--) {
                int digit = part / (int) Math.pow(10, place) % 10;
                if (digit == 0) continue;
                if (digit > 1 || place == 0) words.append(SINO[digit]);
                words.append(small[place]);
            }
            // 10000은 "일만"이 아니라 "만"으로 읽는다.
            if (group == 1 && part == 1) words.setLength(0);
            out.append(words).append(large[group]);
        }
        return out.toString();
    }
}
