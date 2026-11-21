package android.car.hardware.mcu;

import android.car.CarManagerBase;
import android.car.hardware.property.CarPropertyEvent;
import android.car.hardware.property.CarPropertyManager;
import android.content.Context;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

public class CarMcuManager implements CarManagerBase {
    private static final String TAG = "CarMcuManager";

    public interface CarMcuEventCallback {
        void onEvent(CarPropertyEvent event);
    }

    private final CarPropertyManager propertyManager;

    public CarMcuManager(IBinder binder, Context context) {
        this(binder, context, null);
    }

    public CarMcuManager(IBinder binder, Context context, Handler handler) {
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

    public float getFloatProperty(int propId, int areaId) {
        try {
            return propertyManager.getFloatProperty(propId, areaId);
        } catch (Throwable t) {
            Log.w(TAG, "getFloatProperty 0x" + Integer.toHexString(propId) + ": " + t);
            return 0f;
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

    public byte[] getByteArrayProperty(int propId, int areaId) {
        try {
            return propertyManager.getByteArrayProperty(propId, areaId);
        } catch (Throwable t) {
            Log.w(TAG, "getByteArrayProperty 0x" + Integer.toHexString(propId) + ": " + t);
            return new byte[0];
        }
    }

    public String getStringProperty(int propId, int areaId) {
        try {
            return propertyManager.getStringProperty(propId, areaId);
        } catch (Throwable t) {
            Log.w(TAG, "getStringProperty 0x" + Integer.toHexString(propId) + ": " + t);
            return null;
        }
    }

    public void setIntProperty(int propId, int areaId, int value) {
        try {
            propertyManager.setIntProperty(propId, areaId, value);
        } catch (Throwable t) {
            Log.w(TAG, "setIntProperty 0x" + Integer.toHexString(propId) + ": " + t);
        }
    }

    public void registerCallback(CarMcuEventCallback callback, int[] propertyIds) {
    }

    public void unregisterCallback(CarMcuEventCallback callback) {
    }
}
