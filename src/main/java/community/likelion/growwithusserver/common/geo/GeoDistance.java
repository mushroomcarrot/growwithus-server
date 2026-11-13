package community.likelion.growwithusserver.common.geo;

// 두 좌표 사이의 직선 거리 계산
public final class GeoDistance {
    private static final double EARTH_RADIUS_KM = 6371.0;

    private GeoDistance() {
    }

    // 하버사인 공식, 소수 첫째 자리 km
    public static double km(double latitude1, double longitude1, double latitude2, double longitude2) {
        double latitudeDelta = Math.toRadians(latitude2 - latitude1);
        double longitudeDelta = Math.toRadians(longitude2 - longitude1);
        double a = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(Math.toRadians(latitude1)) * Math.cos(Math.toRadians(latitude2))
                * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        double distance = 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));

        return Math.round(distance * 10) / 10.0;
    }
}
