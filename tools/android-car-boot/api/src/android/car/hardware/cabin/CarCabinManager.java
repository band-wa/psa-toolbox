package android.car.hardware.cabin;

import android.car.CarManagerBase;
import android.car.hardware.property.CarPropertyEvent;
import android.car.hardware.property.CarPropertyManager;
import android.content.Context;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

public class CarCabinManager implements CarManagerBase {
    private static final String TAG = "CarCabinManager";

    public interface CarCabinEventCallback {
        void onEvent(CarPropertyEvent event);
    }

    private final CarPropertyManager propertyManager;

    public CarCabinManager(IBinder binder, Context context) {
        this(binder, context, null);
    }

    public CarCabinManager(IBinder binder, Context context, Handler handler) {
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

    public int[] getIntArrayProperty(int propId, int areaId) {
        try {
            return propertyManager.getIntArrayProperty(propId, areaId);
        } catch (Throwable t) {
            Log.w(TAG, "getIntArrayProperty 0x" + Integer.toHexString(propId) + ": " + t);
            return new int[0];
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

    public void setByteArrayProperty(int propId, int areaId, byte[] value) {
        try {
            propertyManager.setByteArrayProperty(propId, areaId, value);
        } catch (Throwable t) {
            Log.w(TAG, "setByteArrayProperty 0x" + Integer.toHexString(propId) + ": " + t);
        }
    }

    public void registerCallback(CarCabinEventCallback callback, int[] propertyIds) {
    }

    public void unregisterCallback(CarCabinEventCallback callback) {
    }
}
