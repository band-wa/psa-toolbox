package android.car;

import android.content.Context;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;

public class Car implements CarManagerBase {
    public static final String PROPERTY_SERVICE = "property";
    public static final String CABIN_SERVICE = "cabin";
    public static final String HVAC_SERVICE = "hvac";
    public static final String INFO_SERVICE = "info";
    public static final String SENSOR_SERVICE = "sensor";
    public static final String AUDIO_SERVICE = "audio";
    public static final String PERMISSION_CAR_POWERTRAIN = "android.car.permission.CAR_POWERTRAIN";

    private Car() {
    }

    public static Car createCar(Context context, ServiceConnection serviceConnectionListener) {
        return new Car();
    }

    public static Car createCar(Context context, ServiceConnection serviceConnectionListener,
            Handler handler) {
        return new Car();
    }

    public void connect() {
    }

    public void disconnect() {
    }

    public boolean isConnected() {
        return false;
    }

    public boolean isConnecting() {
        return false;
    }

    public Object getCarManager(String serviceName) {
        return null;
    }

    public void onServiceConnected(IBinder service) {
    }

    public void onServiceDisconnected() {
    }
}
