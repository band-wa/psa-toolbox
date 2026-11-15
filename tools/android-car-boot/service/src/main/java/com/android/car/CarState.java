package com.android.car;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;

import com.bandwa.vcarlib.BootCarProtocol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CarState {
    // 保留 VirtualCar 便于与参考实现/文档保持一致的 logcat tag；ControlReceiver 的日志排查靠它。
    static final String TAG = "VirtualCar";

    static final int AREA_GLOBAL = 0;
    static final int AREA_DRIVER = 1;
    static final int AREA_PASSENGER = 2;

    static final int PROP_VIN = 0x11100100;
    static final int PROP_VIN_MATCH = 0x2140f3f3;
    static final int PROP_TOTAL_MILEAGE = 0x2160f338;
    static final int PROP_REMAINING_OIL = 0x2140f334;
    static final int PROP_MAINTENANCE_AUTONOMIE = 0x2140f525;
    static final int PROP_FUEL_CONSUMPTION = 0x2140f335;
    static final int PROP_STEERING_ANGLE = 0x21400ae5;
    static final int PROP_SPEED = 0x11600207;
    static final int PROP_GEAR = 0x2140210F;
    static final int PROP_REAR_DOOR_MODE = 0x21400978;
    static final int PROP_REAR_DOOR_MODE_SET = 0x21400977;
    static final int PROP_USB_OTG_MODE = 0x2140F141;
    static final int PROP_AVM_DISPLAY = 557903540;
    static final int PROP_AVM_SWITCH = 557969072;
    static final int PROP_MCU_KEY_INFO = 0x2141F04B;
    static final int PROP_MCU_KEY_POWER_LEFT = 0x2140F04C;
    static final int PROP_MCU_KEY_POWER_RIGHT = 0x2140F04D;
    static final int PROP_HVAC_AC_ON_SET = 0x2540051E;
    static final int PROP_HVAC_TEMPERATURE_ADJUST = 0x25411912;
    static final int PROP_HVAC_FAN_SPEED_ADJUST = 0x25411908;

    private static final long TICK_MS = 100L;

    private static CarState instance;

    static synchronized CarState get() {
        if (instance == null) {
            instance = new CarState();
        }
        return instance;
    }

    static final class Property {
        final int id;
        final int type;
        final int changeMode;
        final boolean writable;
        final float minValue;
        final float maxValue;
        final int[] areaIds;
        volatile Object value;

        Property(int id, int type, int changeMode, boolean writable, Object value, float minValue,
                float maxValue, int[] areaIds) {
            this.id = id;
            this.type = type;
            this.changeMode = changeMode;
            this.writable = writable;
            this.value = value;
            this.minValue = minValue;
            this.maxValue = maxValue;
            this.areaIds = areaIds;
        }
    }

    private static final class Registration {
        final float rate;
        long lastSent;

        Registration(float rate) {
            this.rate = rate;
        }
    }

    private final Map<Integer, Property> properties = new LinkedHashMap<>();
    private final Map<IBinder, Map<Integer, Registration>> registrations = new LinkedHashMap<>();
    private final Map<IBinder, IBinder.DeathRecipient> deathRecipients = new HashMap<>();
    private final HandlerThread thread;
    private final Handler handler;
    private final Handler mainHandler = new Handler();

    private boolean simulationEnabled;
    private long simulationStart;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            try {
                runSimulation();
                pushPeriodicEvents();
            } finally {
                handler.postDelayed(this, TICK_MS);
            }
        }
    };

    private CarState() {
        registerDefaultProperties();
        simulationStart = SystemClock.uptimeMillis();
        thread = new HandlerThread("VirtualCar");
        thread.start();
        handler = new Handler(thread.getLooper());
        handler.postDelayed(ticker, TICK_MS);
        Log.i(TAG, "CarState initialized with " + properties.size() + " properties");
    }

    private void registerDefaultProperties() {
        add(PROP_VIN, BootCarProtocol.TYPE_STRING, false, "VF3XXXXXXXXXXXXXX");
        add(PROP_VIN_MATCH, BootCarProtocol.TYPE_INT, false, 1);
        add(PROP_TOTAL_MILEAGE, BootCarProtocol.TYPE_FLOAT, false, 12345.0f);
        add(PROP_REMAINING_OIL, BootCarProtocol.TYPE_INT, false, 62);
        add(PROP_MAINTENANCE_AUTONOMIE, BootCarProtocol.TYPE_INT, false, 520);
        add(PROP_FUEL_CONSUMPTION, BootCarProtocol.TYPE_INT, false, 6500000);
        add(PROP_STEERING_ANGLE, BootCarProtocol.TYPE_INT, false, 0);
        add(PROP_SPEED, BootCarProtocol.TYPE_FLOAT, false, 0f);
        add(PROP_GEAR, BootCarProtocol.TYPE_INT, false, 4);
        add(PROP_REAR_DOOR_MODE, BootCarProtocol.TYPE_INT, false, 1);
        add(PROP_REAR_DOOR_MODE_SET, BootCarProtocol.TYPE_INT, true, 1);
        add(PROP_USB_OTG_MODE, BootCarProtocol.TYPE_INT, true, 1);
        add(PROP_AVM_DISPLAY, BootCarProtocol.TYPE_INT, false, 0);
        add(PROP_AVM_SWITCH, BootCarProtocol.TYPE_INT_ARRAY, true, new int[] {0, 0});
        add(PROP_MCU_KEY_INFO, BootCarProtocol.TYPE_INT_ARRAY, false, new int[] {0, 0});
        add(PROP_MCU_KEY_POWER_LEFT, BootCarProtocol.TYPE_INT, false, 0);
        add(PROP_MCU_KEY_POWER_RIGHT, BootCarProtocol.TYPE_INT, false, 0);
        add(PROP_HVAC_AC_ON_SET, BootCarProtocol.TYPE_INT, true, 0);
        add(PROP_HVAC_TEMPERATURE_ADJUST, BootCarProtocol.TYPE_INT_ARRAY, true,
                new int[] {0, 0});
        add(PROP_HVAC_FAN_SPEED_ADJUST, BootCarProtocol.TYPE_INT_ARRAY, true, new int[] {0, 0});
    }

    private void add(int id, int type, boolean writable, Object value) {
        int changeMode = BootCarProtocol.TYPE_INT_ARRAY == type
                ? 2
                : (type == BootCarProtocol.TYPE_INT || type == BootCarProtocol.TYPE_FLOAT)
                        ? 2 : 2;
        properties.put(id, new Property(id, type, changeMode, writable, value, 0f, 0f,
                new int[] {AREA_GLOBAL, AREA_DRIVER}));
    }

    synchronized Property property(int id) {
        return properties.get(id);
    }

    synchronized int typeOf(int id) {
        Property property = properties.get(id);
        return property == null ? BootCarProtocol.TYPE_NULL : property.type;
    }

    int write(int propId, Object value) {
        return writeInternal(propId, value, true);
    }

    int forceWrite(int propId, Object value) {
        return writeInternal(propId, value, false);
    }

    private int writeInternal(int propId, Object value, boolean checkWritable) {
        Property property;
        Object coerced;
        List<Property> changed = new ArrayList<>();
        synchronized (this) {
            property = properties.get(propId);
            if (property == null) {
                return BootCarProtocol.RESULT_NOT_FOUND;
            }
            if (checkWritable && !property.writable) {
                return BootCarProtocol.RESULT_NOT_WRITABLE;
            }
            coerced = BootCarProtocol.coerce(property.type, value);
            if (coerced == null) {
                return BootCarProtocol.RESULT_TYPE_MISMATCH;
            }
            applyLocked(property, coerced, changed);
        }
        for (Property item : changed) {
            fireChanged(item);
        }
        return BootCarProtocol.RESULT_OK;
    }

    synchronized void reset() {
        String vin = (String) properties.get(PROP_VIN).value;
        int previousSize = properties.size();
        properties.clear();
        registerDefaultProperties();
        properties.get(PROP_VIN).value = vin;
        simulationEnabled = false;
        Log.i(TAG, "CarState reset (" + previousSize + " -> " + properties.size() + " properties)");
    }

    void setSimulation(boolean enabled) {
        synchronized (this) {
            simulationEnabled = enabled;
            simulationStart = SystemClock.uptimeMillis();
        }
        Log.i(TAG, "simulation " + (enabled ? "enabled" : "disabled"));
    }

    synchronized boolean isSimulationEnabled() {
        return simulationEnabled;
    }

    synchronized List<Property> properties() {
        return new ArrayList<>(properties.values());
    }

    synchronized String snapshot() {
        StringBuilder builder = new StringBuilder();
        for (Property property : properties.values()) {
            builder.append("0x").append(Integer.toHexString(property.id)).append('=')
                    .append(formatValue(property.type, property.value)).append("  ");
        }
        return builder.toString();
    }

    private static String formatValue(int type, Object value) {
        if (type == BootCarProtocol.TYPE_INT_ARRAY && value instanceof int[]) {
            int[] array = (int[]) value;
            StringBuilder builder = new StringBuilder("[");
            for (int i = 0; i < array.length; i++) {
                if (i > 0) {
                    builder.append(',');
                }
                builder.append(array[i]);
            }
            return builder.append(']').toString();
        }
        return String.valueOf(value);
    }

    private void applyLocked(Property property, Object value, List<Property> changed) {
        property.value = value;
        changed.add(property);
        if (property.id == PROP_REAR_DOOR_MODE_SET) {
            Property mode = properties.get(PROP_REAR_DOOR_MODE);
            if (mode != null) {
                mode.value = value;
                changed.add(mode);
            }
        } else if (property.id == PROP_AVM_SWITCH && value instanceof int[]) {
            int[] array = (int[]) value;
            Property display = properties.get(PROP_AVM_DISPLAY);
            if (display != null) {
                int next = array.length > 0 && array[0] == 1 ? 2 : 0;
                if (!Integer.valueOf(next).equals(display.value)) {
                    display.value = next;
                    changed.add(display);
                }
            }
        } else if (property.id == PROP_MCU_KEY_INFO && value instanceof int[]) {
            int[] array = (int[]) value;
            if (array.length >= 2) {
                applyPowerKeyLocked(PROP_MCU_KEY_POWER_LEFT, array, changed);
                applyPowerKeyLocked(PROP_MCU_KEY_POWER_RIGHT, array, changed);
            }
        }
    }

    private void applyPowerKeyLocked(int targetId, int[] keyInfo, List<Property> changed) {
        Property target = properties.get(targetId);
        if (target == null || keyInfo[0] != targetId) {
            return;
        }
        target.value = keyInfo[1];
        changed.add(target);
    }

    private void fireChanged(Property property) {
        List<IBinder> targets;
        synchronized (this) {
            targets = new ArrayList<>();
            for (Map.Entry<IBinder, Map<Integer, Registration>> entry : registrations
                    .entrySet()) {
                if (entry.getValue().containsKey(property.id)) {
                    targets.add(entry.getKey());
                }
            }
        }
        for (IBinder target : targets) {
            sendEvent(target, property);
        }
    }

    private void sendEvent(IBinder target, Property property) {
        Object value = property.value;
        Parcel data = Parcel.obtain();
        try {
            BootCarProtocol.writeEvent(data, property.id, AREA_GLOBAL,
                    BootCarProtocol.STATUS_AVAILABLE, SystemClock.uptimeMillis(), property.type,
                    value);
            target.transact(BootCarProtocol.TX_EVENT, data, null, IBinder.FLAG_ONEWAY);
        } catch (RemoteException e) {
            removeRegistration(target);
        } finally {
            data.recycle();
        }
    }

    boolean register(int propId, float rate, IBinder callback) {
        if (callback == null) {
            return false;
        }
        Property property;
        synchronized (this) {
            property = properties.get(propId);
            if (property == null) {
                return false;
            }
            Map<Integer, Registration> map = registrations.get(callback);
            if (map == null) {
                map = new LinkedHashMap<>();
                registrations.put(callback, map);
                IBinder.DeathRecipient recipient = new IBinder.DeathRecipient() {
                    @Override
                    public void binderDied() {
                        removeRegistration(callback);
                    }
                };
                deathRecipients.put(callback, recipient);
                try {
                    callback.linkToDeath(recipient, 0);
                } catch (RemoteException e) {
                    deathRecipients.remove(callback);
                    registrations.remove(callback);
                    return false;
                }
            }
            Registration registration = new Registration(rate > 0f ? rate : 0f);
            registration.lastSent = SystemClock.uptimeMillis();
            map.put(propId, registration);
        }
        sendEvent(callback, property);
        Log.i(TAG, "register 0x" + Integer.toHexString(propId) + " rate=" + rate);
        return true;
    }

    void unregister(int propId, IBinder callback) {
        if (callback == null) {
            return;
        }
        boolean removeAll;
        synchronized (this) {
            Map<Integer, Registration> map = registrations.get(callback);
            if (map == null) {
                return;
            }
            if (propId < 0) {
                removeAll = true;
            } else {
                map.remove(propId);
                removeAll = map.isEmpty();
            }
            if (removeAll) {
                registrations.remove(callback);
                IBinder.DeathRecipient recipient = deathRecipients.remove(callback);
                if (recipient != null) {
                    callback.unlinkToDeath(recipient, 0);
                }
            }
        }
        Log.i(TAG, "unregister 0x" + Integer.toHexString(propId) + " all=" + removeAll);
    }

    private void removeRegistration(IBinder callback) {
        synchronized (this) {
            registrations.remove(callback);
            IBinder.DeathRecipient recipient = deathRecipients.remove(callback);
            if (recipient != null) {
                callback.unlinkToDeath(recipient, 0);
            }
        }
    }

    private void runSimulation() {
        boolean enabled;
        synchronized (this) {
            enabled = simulationEnabled;
        }
        if (!enabled) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        float seconds = (now - simulationStart) / 1000f;
        List<Property> changed = new ArrayList<>();
        synchronized (this) {
            Property speed = properties.get(PROP_SPEED);
            if (speed != null) {
                float value = (float) (60.0 + 60.0 * Math.sin(seconds / 4.0));
                speed.value = value;
                changed.add(speed);
            }
            Property steering = properties.get(PROP_STEERING_ANGLE);
            if (steering != null) {
                int value = (int) (540.0 * Math.sin(seconds / 2.0));
                steering.value = value;
                changed.add(steering);
            }
            Property mileage = properties.get(PROP_TOTAL_MILEAGE);
            if (mileage != null && mileage.value instanceof Number) {
                float delta = ((Number) mileage.value).floatValue() == 0f ? 0f
                        : (float) (60.0 / 3600.0 * TICK_MS / 1000.0);
                mileage.value = ((Number) mileage.value).floatValue() + delta;
                changed.add(mileage);
            }
        }
        for (Property property : changed) {
            fireChanged(property);
        }
    }

    private void pushPeriodicEvents() {
        List<Object[]> pending = new ArrayList<>();
        long now = SystemClock.uptimeMillis();
        synchronized (this) {
            for (Map.Entry<IBinder, Map<Integer, Registration>> entry : registrations
                    .entrySet()) {
                for (Map.Entry<Integer, Registration> item : entry.getValue().entrySet()) {
                    Registration registration = item.getValue();
                    if (registration.rate <= 0f) {
                        continue;
                    }
                    long interval = Math.max(TICK_MS, (long) (1000f / registration.rate));
                    if (now - registration.lastSent < interval) {
                        continue;
                    }
                    Property property = properties.get(item.getKey());
                    if (property == null) {
                        continue;
                    }
                    registration.lastSent = now;
                    pending.add(new Object[] {entry.getKey(), property});
                }
            }
        }
        for (Object[] item : pending) {
            sendEvent((IBinder) item[0], (Property) item[1]);
        }
    }

    void pressMcuKey(int keyId, int keyState) {
        write(PROP_MCU_KEY_INFO, new int[] {keyId, keyState});
        Log.i(TAG, "mcu key 0x" + Integer.toHexString(keyId) + " state=" + keyState);
    }

    Handler mainHandler() {
        return mainHandler;
    }
}
