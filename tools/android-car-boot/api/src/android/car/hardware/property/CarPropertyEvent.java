package android.car.hardware.property;

import android.car.hardware.CarPropertyValue;

public class CarPropertyEvent {
    public static final int PROPERTY_EVENT_PROPERTY_CHANGE = 0;
    public static final int PROPERTY_EVENT_ERROR = 1;

    private final int eventType;
    private final CarPropertyValue<?> value;

    public CarPropertyEvent(int eventType, CarPropertyValue<?> value) {
        this.eventType = eventType;
        this.value = value;
    }

    public int getEventType() {
        return eventType;
    }

    public CarPropertyValue<?> getCarPropertyValue() {
        return value;
    }
}
