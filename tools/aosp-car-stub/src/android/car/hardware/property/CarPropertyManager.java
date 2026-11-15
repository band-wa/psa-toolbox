package android.car.hardware.property;

import android.car.CarManagerBase;
import android.car.CarNotConnectedException;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;

import java.util.List;

public class CarPropertyManager implements CarManagerBase {
    public interface CarPropertyEventListener {
        void onChangeEvent(CarPropertyValue value);

        void onErrorEvent(int propId, int areaId);
    }

    public CarPropertyManager() {
    }

    public <E> CarPropertyValue<E> getProperty(int propId, int areaId)
            throws CarNotConnectedException {
        return null;
    }

    public <E> CarPropertyValue<E> getProperty(Class<E> clazz, int propId, int areaId)
            throws CarNotConnectedException {
        return null;
    }

    public int getIntProperty(int propId, int areaId) throws CarNotConnectedException {
        return 0;
    }

    public boolean getBooleanProperty(int propId, int areaId) throws CarNotConnectedException {
        return false;
    }

    public float getFloatProperty(int propId, int areaId) throws CarNotConnectedException {
        return 0f;
    }

    public String getStringProperty(int propId, int areaId) throws CarNotConnectedException {
        return null;
    }

    public void setIntProperty(int propId, int areaId, int val) throws CarNotConnectedException {
    }

    public void setBooleanProperty(int propId, int areaId, boolean val)
            throws CarNotConnectedException {
    }

    public void setFloatProperty(int propId, int areaId, float val)
            throws CarNotConnectedException {
    }

    public void setStringProperty(int propId, int areaId, String val)
            throws CarNotConnectedException {
    }

    public void setIntArrayProperty(int propId, int areaId, int[] val)
            throws CarNotConnectedException {
    }

    public <E> void setProperty(Class<E> clazz, int propId, int areaId, E val)
            throws CarNotConnectedException {
    }

    public boolean registerListener(CarPropertyEventListener listener, int propId, float rate)
            throws CarNotConnectedException {
        return false;
    }

    public void unregisterListener(CarPropertyEventListener listener) {
    }

    public void unregisterListener(CarPropertyEventListener listener, int propId) {
    }

    public List<CarPropertyConfig> getPropertyList() {
        return null;
    }
}
