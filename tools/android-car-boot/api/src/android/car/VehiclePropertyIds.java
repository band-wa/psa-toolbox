package android.car;

/**
 * AOSP {@code VehiclePropertyIds} 的精简实现：厂商代码只用于日志/描述，
 * 这里保证 {@code toString(int)} 存在且不抛异常。
 */
public final class VehiclePropertyIds {
    private VehiclePropertyIds() {
    }

    public static String toString(int propertyId) {
        return "0x" + String.format("%08x", propertyId);
    }
}
