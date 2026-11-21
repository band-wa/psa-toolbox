package android.car.hardware;

import android.car.CarManagerBase;
import android.car.hardware.property.CarPropertyManager;
import android.content.Context;
import android.os.Handler;
import android.os.IBinder;

/** 无真实传感器：读回 0 ℃ 事件，监听注册成功但不投递事件。 */
public class CarSensorManager implements CarManagerBase {
    public interface OnSensorChangedListener {
        void onSensorChanged(CarSensorEvent event);
    }

    private final CarPropertyManager propertyManager;

    public CarSensorManager(IBinder binder, Context context) {
        this(binder, context, null);
    }

    public CarSensorManager(IBinder binder, Context context, Handler handler) {
        this.propertyManager = new CarPropertyManager(binder, handler);
    }

    public CarSensorEvent getLatestSensorEvent(int sensorType) {
        CarSensorEvent event = new CarSensorEvent();
        event.sensorType = sensorType;
        event.timestamp = android.os.SystemClock.elapsedRealtime();
        Object value = null;
        try {
            value = propertyManager.getProperty(sensorType, 0);
        } catch (Throwable ignored) {
        }
        if (value != null) {
            Object raw = ((android.car.hardware.CarPropertyValue<?>) value).getValue();
            if (raw instanceof Number) {
                event.floatValues = new float[] {((Number) raw).floatValue()};
                return event;
            }
        }
        event.floatValues = new float[] {0f};
        return event;
    }

    public boolean registerListener(OnSensorChangedListener listener, int sensorType, int rate) {
        return true;
    }

    public void unregisterListener(OnSensorChangedListener listener) {
    }
}
