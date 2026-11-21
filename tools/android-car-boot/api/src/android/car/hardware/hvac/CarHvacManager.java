package android.car.hardware.hvac;

import android.car.CarManagerBase;
import android.car.hardware.property.CarPropertyEvent;
import android.car.hardware.property.CarPropertyManager;
import android.content.Context;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

/** 属性读写走 CarPropertyManager；未知厂商属性的异常在此吞掉。 */
public class CarHvacManager implements CarManagerBase {
    private static final String TAG = "CarHvacManager";

    public interface CarHvacEventCallback {
        void onEvent(CarPropertyEvent event);
    }

    private final CarPropertyManager propertyManager;

    public CarHvacManager(IBinder binder, Context context) {
        this(binder, context, null);
    }

    public CarHvacManager(IBinder binder, Context context, Handler handler) {
        this.propertyManager = new CarPropertyManager(binder, handler);
    }

    public int getIntProperty(int propId, int areaId) {
        try {
            return propertyManager.getIntProperty(propId, areaId);
        } catch (Throwable t) {
            Log.w(TAG, "getIntProperty 0x" + Integer.toHexString(propId) + ": " + t);
            return 0;
        }
    }

    public void setIntProperty(int propId, int areaId, int value) {
        try {
            propertyManager.setIntProperty(propId, areaId, value);
        } catch (Throwable t) {
            Log.w(TAG, "setIntProperty 0x" + Integer.toHexString(propId) + ": " + t);
        }
    }

    public void setIntArrayProperty(int propId, int areaId, int[] value) {
        try {
            propertyManager.setIntArrayProperty(propId, areaId, value);
        } catch (Throwable t) {
            Log.w(TAG, "setIntArrayProperty 0x" + Integer.toHexString(propId) + ": " + t);
        }
    }

    public float getFloatProperty(int propId, int areaId) {
        try {
            return propertyManager.getFloatProperty(propId, areaId);
        } catch (Throwable t) {
            Log.w(TAG, "getFloatProperty 0x" + Integer.toHexString(propId) + ": " + t);
            return 0f;
        }
    }

    public void setFloatProperty(int propId, int areaId, float value) {
        try {
            propertyManager.setFloatProperty(propId, areaId, value);
        } catch (Throwable t) {
            Log.w(TAG, "setFloatProperty 0x" + Integer.toHexString(propId) + ": " + t);
        }
    }

    public void registerCallback(CarHvacEventCallback callback) {
    }

    public void unregisterCallback(CarHvacEventCallback callback) {
    }
}
