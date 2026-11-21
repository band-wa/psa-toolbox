package android.car.hardware.tbox;

import android.car.CarManagerBase;
import android.car.hardware.property.CarPropertyEvent;
import android.car.hardware.property.CarPropertyManager;
import android.content.Context;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

public class CarTBoxManager implements CarManagerBase {
    private static final String TAG = "CarTBoxManager";

    public interface CarTBoxEventCallback {
        void onEvent(CarPropertyEvent event);
    }

    private final CarPropertyManager propertyManager;

    public CarTBoxManager(IBinder binder, Context context) {
        this(binder, context, null);
    }

    public CarTBoxManager(IBinder binder, Context context, Handler handler) {
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

    public void registerCallback(CarTBoxEventCallback callback, int[] propertyIds) {
    }

    public void unregisterCallback(CarTBoxEventCallback callback) {
    }
}
