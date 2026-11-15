package android.car.hardware.property;

import android.car.CarManagerBase;
import android.car.CarNotConnectedException;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.ArraySet;

import com.bandwa.vcarlib.BootCarProtocol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通过 Binder 事务访问车机 CarService 的属性管理器。
 *
 * <p>wire format 见共享协议 {@link BootCarProtocol}；所有调用在
 * binder 失效时抛 {@link CarNotConnectedException}，属性不存在时 get 返回 null、set 抛
 * IllegalArgumentException（与 AOSP 客户端语义大致相符）。int[] 结果统一转成 Integer[]，
 * 以匹配 Kotlin 侧 {@code value as? Array<*>} 的用法。
 */
public class CarPropertyManager implements CarManagerBase {
    public interface CarPropertyEventListener {
        void onChangeEvent(CarPropertyValue value);

        void onErrorEvent(int propId, int areaId);
    }

    private final IBinder binder;
    private final Handler handler;
    private final Map<CarPropertyEventListener, ListenerCallback> callbacks = new HashMap<>();

    public CarPropertyManager() {
        this(null);
    }

    public CarPropertyManager(IBinder binder) {
        this(binder, null);
    }

    public CarPropertyManager(IBinder binder, Handler handler) {
        this.binder = binder;
        this.handler = handler != null ? handler : new Handler(Looper.getMainLooper());
    }

    /**
     * AOSP / 厂商 car-lib 的内部构造签名。
     *
     * <p>bootclasspath 覆盖后，厂商自带的 CarCabinManager 等类会以
     * {@code new CarPropertyManager(binder, handler, false, tag)} 构造本类；缺这个重载会在
     * 反射创建厂商管理器时抛 NoSuchMethodError，进而使 getCarManager 返回 null。
     */
    public CarPropertyManager(IBinder binder, Handler handler, boolean dbg, String tag) {
        this(binder, handler);
    }

    public <E> CarPropertyValue<E> getProperty(int propId, int areaId)
            throws CarNotConnectedException {
        Parcel data = Parcel.obtain();
        data.writeInt(propId);
        data.writeInt(areaId);
        Parcel reply = transact(BootCarProtocol.TX_GET, data);
        try {
            int result = reply.readInt();
            if (result != BootCarProtocol.RESULT_OK) {
                return null;
            }
            int type = reply.readInt();
            long timestamp = reply.readLong();
            int status = reply.readInt();
            Object value = BootCarProtocol.readValue(reply, type);
            if (value instanceof int[]) {
                value = BootCarProtocol.toIntegerArray((int[]) value);
            }
            @SuppressWarnings("unchecked")
            CarPropertyValue<E> propertyValue = new CarPropertyValue<E>(propId, areaId, status,
                    timestamp, (E) value);
            return propertyValue;
        } finally {
            reply.recycle();
        }
    }

    public <E> CarPropertyValue<E> getProperty(Class<E> clazz, int propId, int areaId)
            throws CarNotConnectedException {
        return getProperty(propId, areaId);
    }

    public int getIntProperty(int propId, int areaId) throws CarNotConnectedException {
        CarPropertyValue<?> value = getProperty(propId, areaId);
        if (value == null || !(value.getValue() instanceof Number)) {
            return 0;
        }
        return ((Number) value.getValue()).intValue();
    }

    public boolean getBooleanProperty(int propId, int areaId) throws CarNotConnectedException {
        CarPropertyValue<?> value = getProperty(propId, areaId);
        if (value == null) {
            return false;
        }
        Object raw = value.getValue();
        if (raw instanceof Boolean) {
            return (Boolean) raw;
        }
        return raw instanceof Number && ((Number) raw).intValue() != 0;
    }

    public float getFloatProperty(int propId, int areaId) throws CarNotConnectedException {
        CarPropertyValue<?> value = getProperty(propId, areaId);
        if (value == null || !(value.getValue() instanceof Number)) {
            return 0f;
        }
        return ((Number) value.getValue()).floatValue();
    }

    public String getStringProperty(int propId, int areaId) throws CarNotConnectedException {
        CarPropertyValue<?> value = getProperty(propId, areaId);
        return value == null || value.getValue() == null ? null : String.valueOf(value.getValue());
    }

    public void setIntProperty(int propId, int areaId, int val) throws CarNotConnectedException {
        setValue(propId, areaId, BootCarProtocol.TYPE_INT, val);
    }

    public void setBooleanProperty(int propId, int areaId, boolean val)
            throws CarNotConnectedException {
        setValue(propId, areaId, BootCarProtocol.TYPE_BOOL, val);
    }

    public void setFloatProperty(int propId, int areaId, float val)
            throws CarNotConnectedException {
        setValue(propId, areaId, BootCarProtocol.TYPE_FLOAT, val);
    }

    public void setStringProperty(int propId, int areaId, String val)
            throws CarNotConnectedException {
        setValue(propId, areaId, BootCarProtocol.TYPE_STRING, val);
    }

    public void setIntArrayProperty(int propId, int areaId, int[] val)
            throws CarNotConnectedException {
        setValue(propId, areaId, BootCarProtocol.TYPE_INT_ARRAY, val);
    }

    /** AOSP car-lib 的 byte[] 属性 setter（厂商管理器/车载配置使用）。 */
    public void setByteArrayProperty(int propId, int areaId, byte[] val)
            throws CarNotConnectedException {
        setValue(propId, areaId, BootCarProtocol.TYPE_BYTE_ARRAY, val);
    }

    /** AOSP car-lib 的 float[] 属性 setter（厂商管理器使用）。 */
    public void setFloatArrayProperty(int propId, int areaId, float[] val)
            throws CarNotConnectedException {
        setValue(propId, areaId, BootCarProtocol.TYPE_FLOAT_ARRAY, val);
    }

    /** AOSP car-lib 的 float[] 属性 getter。AOSP 语义：不存在或类型不符返回空数组。 */
    public float[] getFloatArrayProperty(int propId, int areaId)
            throws CarNotConnectedException {
        CarPropertyValue<?> propertyValue = getProperty(propId, areaId);
        if (propertyValue == null) {
            return new float[0];
        }
        Object value = propertyValue.getValue();
        return value instanceof float[] ? (float[]) value : new float[0];
    }

    /** AOSP car-lib 的 byte[] 属性 getter。AOSP 语义：不存在或类型不符返回空数组。 */
    public byte[] getByteArrayProperty(int propId, int areaId)
            throws CarNotConnectedException {
        CarPropertyValue<?> propertyValue = getProperty(propId, areaId);
        if (propertyValue == null) {
            return new byte[0];
        }
        Object value = propertyValue.getValue();
        return value instanceof byte[] ? (byte[]) value : new byte[0];
    }

    /**
     * AOSP car-lib 的 int[] 属性 getter。AOSP 语义：不存在或类型不符返回空数组；
     * 内部 int[] 与 Integer[] 表示都接受（我们的 getProperty 会把 int[] 归一化为 Integer[]）。
     */
    public int[] getIntArrayProperty(int propId, int areaId)
            throws CarNotConnectedException {
        CarPropertyValue<?> propertyValue = getProperty(propId, areaId);
        if (propertyValue == null) {
            return new int[0];
        }
        int[] value = BootCarProtocol.toIntArray(propertyValue.getValue());
        return value != null ? value : new int[0];
    }

    /**
     * AOSP car-lib 的按属性 ID 过滤的配置列表。自定义协议只提供全量列表，这里做过滤。
     */
    public List<CarPropertyConfig> getPropertyList(ArraySet<Integer> propertyIds) {
        List<CarPropertyConfig> all = getPropertyList();
        if (all == null || propertyIds == null || propertyIds.isEmpty()) {
            return all;
        }
        List<CarPropertyConfig> filtered = new ArrayList<>();
        for (CarPropertyConfig config : all) {
            if (config != null && propertyIds.contains(config.getPropertyId())) {
                filtered.add(config);
            }
        }
        return filtered;
    }

    /** AOSP car-lib：可读属性配置列表（本实现不做读写区分，等同 getPropertyList）。 */
    public List<CarPropertyConfig> getReadablePropertyList(ArraySet<Integer> propertyIds) {
        return getPropertyList(propertyIds);
    }

    /** AOSP car-lib：属性/区域是否可用。不存在时返回 false。 */
    public boolean isPropertyAvailable(int propId, int areaId) throws CarNotConnectedException {
        return getProperty(propId, areaId) != null;
    }

    /** AOSP car-lib：Car 断连回调。本实现无本地资源需释放。 */
    public void onCarDisconnected() {
    }

    public <E> void setProperty(Class<E> clazz, int propId, int areaId, E val)
            throws CarNotConnectedException {
        int type;
        if (clazz == Integer.class || clazz == int.class) {
            type = BootCarProtocol.TYPE_INT;
        } else if (clazz == Float.class || clazz == float.class) {
            type = BootCarProtocol.TYPE_FLOAT;
        } else if (clazz == Boolean.class || clazz == boolean.class) {
            type = BootCarProtocol.TYPE_BOOL;
        } else if (clazz == String.class) {
            type = BootCarProtocol.TYPE_STRING;
        } else if (clazz == int[].class || clazz == Integer[].class) {
            type = BootCarProtocol.TYPE_INT_ARRAY;
        } else if (clazz == byte[].class) {
            type = BootCarProtocol.TYPE_BYTE_ARRAY;
        } else if (clazz == float[].class) {
            type = BootCarProtocol.TYPE_FLOAT_ARRAY;
        } else {
            throw new IllegalArgumentException("Unsupported property type: " + clazz);
        }
        setValue(propId, areaId, type, val);
    }

    public boolean registerListener(CarPropertyEventListener listener, int propId, float rate)
            throws CarNotConnectedException {
        if (listener == null) {
            return false;
        }
        ListenerCallback callback = callbacks.get(listener);
        if (callback == null) {
            callback = new ListenerCallback(listener);
            callbacks.put(listener, callback);
        }
        Parcel data = Parcel.obtain();
        data.writeInt(propId);
        data.writeFloat(rate);
        data.writeStrongBinder(callback);
        Parcel reply = transact(BootCarProtocol.TX_REGISTER, data);
        try {
            return reply.readInt() == BootCarProtocol.RESULT_OK;
        } finally {
            reply.recycle();
        }
    }

    public void unregisterListener(CarPropertyEventListener listener) {
        if (listener == null) {
            return;
        }
        ListenerCallback callback = callbacks.remove(listener);
        if (callback == null) {
            return;
        }
        unregister(-1, callback);
    }

    public void unregisterListener(CarPropertyEventListener listener, int propId) {
        if (listener == null) {
            return;
        }
        ListenerCallback callback = callbacks.get(listener);
        if (callback == null) {
            return;
        }
        unregister(propId, callback);
    }

    public List<CarPropertyConfig> getPropertyList() {
        final IBinder current = binder;
        if (current == null || !current.isBinderAlive()) {
            return null;
        }
        Parcel data = Parcel.obtain();
        Parcel reply;
        try {
            reply = transact(BootCarProtocol.TX_GET_CONFIGS, data);
        } catch (CarNotConnectedException e) {
            return null;
        }
        try {
            int count = reply.readInt();
            List<CarPropertyConfig> configs = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int propId = reply.readInt();
                int type = reply.readInt();
                int changeMode = reply.readInt();
                boolean writable = reply.readInt() != 0;
                float minValue = reply.readFloat();
                float maxValue = reply.readFloat();
                int areaCount = reply.readInt();
                int[] areas = new int[areaCount];
                for (int j = 0; j < areaCount; j++) {
                    areas[j] = reply.readInt();
                }
                configs.add(new CarPropertyConfig<Object>(propId, type, changeMode, writable, areas,
                        minValue, maxValue));
            }
            return configs;
        } finally {
            reply.recycle();
        }
    }

    private void unregister(int propId, ListenerCallback callback) {
        final IBinder current = binder;
        if (current == null || !current.isBinderAlive()) {
            return;
        }
        Parcel data = Parcel.obtain();
        data.writeInt(propId);
        data.writeStrongBinder(callback);
        try {
            current.transact(BootCarProtocol.TX_UNREGISTER, data, null, 0);
        } catch (RemoteException ignored) {
        } finally {
            data.recycle();
        }
    }

    private void setValue(int propId, int areaId, int type, Object value)
            throws CarNotConnectedException {
        Parcel data = Parcel.obtain();
        data.writeInt(propId);
        data.writeInt(areaId);
        data.writeInt(type);
        BootCarProtocol.writeValue(data, type, value);
        Parcel reply = transact(BootCarProtocol.TX_SET, data);
        try {
            int result = reply.readInt();
            if (result == BootCarProtocol.RESULT_NOT_WRITABLE) {
                throw new IllegalArgumentException(
                        "Property 0x" + Integer.toHexString(propId) + " is not writable");
            }
            if (result == BootCarProtocol.RESULT_NOT_FOUND) {
                throw new IllegalArgumentException(
                        "Unknown property 0x" + Integer.toHexString(propId));
            }
            if (result == BootCarProtocol.RESULT_TYPE_MISMATCH) {
                throw new IllegalArgumentException(
                        "Type mismatch for property 0x" + Integer.toHexString(propId));
            }
        } finally {
            reply.recycle();
        }
    }

    private Parcel transact(int code, Parcel data) throws CarNotConnectedException {
        final IBinder current = binder;
        if (current == null || !current.isBinderAlive()) {
            data.recycle();
            throw new CarNotConnectedException("Car service is not connected");
        }
        Parcel reply = Parcel.obtain();
        try {
            data.setDataPosition(0);
            current.transact(code, data, reply, 0);
            reply.setDataPosition(0);
            return reply;
        } catch (RemoteException e) {
            reply.recycle();
            throw new CarNotConnectedException(e);
        } finally {
            data.recycle();
        }
    }

    private final class ListenerCallback extends Binder {
        private final CarPropertyEventListener listener;

        ListenerCallback(CarPropertyEventListener listener) {
            this.listener = listener;
        }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code != BootCarProtocol.TX_EVENT) {
                return super.onTransact(code, data, reply, flags);
            }
            int propId = data.readInt();
            int areaId = data.readInt();
            int status = data.readInt();
            long timestamp = data.readLong();
            int type = data.readInt();
            Object value = BootCarProtocol.readValue(data, type);
            if (value instanceof int[]) {
                value = BootCarProtocol.toIntegerArray((int[]) value);
            }
            final CarPropertyValue<Object> propertyValue = new CarPropertyValue<Object>(propId,
                    areaId, status, timestamp, value);
            handler.post(new Runnable() {
                @Override
                public void run() {
                    listener.onChangeEvent(propertyValue);
                }
            });
            return true;
        }
    }
}
