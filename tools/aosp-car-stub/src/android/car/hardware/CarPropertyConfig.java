package android.car.hardware;

import java.util.List;

public class CarPropertyConfig<T> {
    public static final int CHANGE_MODE_STATIC = 0x01;
    public static final int CHANGE_MODE_ON_CHANGE = 0x02;
    public static final int CHANGE_MODE_CONTINUOUS = 0x04;

    public int getPropertyId() {
        return 0;
    }

    public int getPropertyType() {
        return 0;
    }

    public int getChangeMode() {
        return CHANGE_MODE_ON_CHANGE;
    }

    public List<Integer> getAreaIds() {
        return null;
    }

    public int getMinValue() {
        return 0;
    }

    public int getMaxValue() {
        return 0;
    }

    public float getMinValue(float defaultValue) {
        return defaultValue;
    }

    public float getMaxValue(float defaultValue) {
        return defaultValue;
    }

    public boolean isWritable() {
        return false;
    }
}
