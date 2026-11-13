package community.likelion.growwithusserver.farm.service;

// 위경도를 기상청 5km 격자(nx, ny)로 바꾼다. 활용가이드의 람베르트 정각원추도법 예제를 옮긴 것이다 (docs/public-data.md).
public final class KmaGrid {
    private static final double RE = 6371.00877;   // 지구 반경 (km)
    private static final double GRID = 5.0;        // 격자 간격 (km)
    private static final double SLAT1 = 30.0;      // 표준위도 1
    private static final double SLAT2 = 60.0;      // 표준위도 2
    private static final double OLON = 126.0;      // 기준점 경도
    private static final double OLAT = 38.0;       // 기준점 위도
    private static final double XO = 43;           // 기준점 X
    private static final double YO = 136;          // 기준점 Y

    private KmaGrid() {
    }

    // {nx, ny}
    public static int[] toGrid(double latitude, double longitude) {
        double degrad = Math.PI / 180.0;
        double re = RE / GRID;
        double slat1 = SLAT1 * degrad;
        double slat2 = SLAT2 * degrad;
        double olon = OLON * degrad;
        double olat = OLAT * degrad;

        double sn = Math.log(Math.cos(slat1) / Math.cos(slat2))
                / Math.log(Math.tan(Math.PI * 0.25 + slat2 * 0.5) / Math.tan(Math.PI * 0.25 + slat1 * 0.5));
        double sf = Math.pow(Math.tan(Math.PI * 0.25 + slat1 * 0.5), sn) * Math.cos(slat1) / sn;
        double ro = re * sf / Math.pow(Math.tan(Math.PI * 0.25 + olat * 0.5), sn);

        double ra = re * sf / Math.pow(Math.tan(Math.PI * 0.25 + latitude * degrad * 0.5), sn);
        double theta = longitude * degrad - olon;
        if (theta > Math.PI) {
            theta -= 2.0 * Math.PI;
        }
        if (theta < -Math.PI) {
            theta += 2.0 * Math.PI;
        }
        theta *= sn;

        int nx = (int) Math.floor(ra * Math.sin(theta) + XO + 0.5);
        int ny = (int) Math.floor(ro - ra * Math.cos(theta) + YO + 0.5);
        return new int[]{nx, ny};
    }
}
