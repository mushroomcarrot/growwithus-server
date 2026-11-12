package community.likelion.growwithusserver.support;

import java.lang.reflect.Proxy;
import java.util.function.BiFunction;

/** DB가 필요 없는 서비스 경계 검사. SQL 및 잠금 검사를 대신하지 않는다. */
public final class FakeRepository {
    private FakeRepository() {}
    public static <T> T of(Class<T> type, BiFunction<String, Object[], Object> call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (method.getName().equals("toString")) return type.getSimpleName();
            return call.apply(method.getName(), args == null ? new Object[0] : args);
        }));
    }
}
