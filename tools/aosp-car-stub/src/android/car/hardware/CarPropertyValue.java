package android.car.hardware;

public class CarPropertyValue<T> {
    public static final int STATUS_AVAILABLE = 0;
    public static final int STATUS_UNAVAILABLE = 1;
    public static final int STATUS_ERROR = 2;

    public CarPropertyValue(int propertyId, int areaId, T value) {
    }

    public CarPropertyValue(int propertyId, int areaId, int status, long timestamp, T value) {
    }

    public int getPropertyId() {
        return 0;
    }

    public int getAreaId() {
        return 0;
    }

    public int getStatus() {
        return STATUS_UNAVAILABLE;
    }

    public long getTimestamp() {
        return 0L;
    }

    public T getValue() {
        return null;
    }

    @Override
    public String toString() {
        return "CarPropertyValue";
    }
}
