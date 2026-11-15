package android.car;

import android.car.hardware.property.CarPropertyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

/**
 * 真实 android.car 客户端（bootclasspath 实现）。
 *
 * <p>与 tools/aosp-car-stub 的 no-op 客户端公开签名保持一致，供已按桩编译的应用使用；
 * 但真正绑定车机系统内的 CarService（com.android.car 包），而不是虚拟车服务。
 */
public class Car implements CarManagerBase {
    public static final String PROPERTY_SERVICE = "property";
    public static final String CABIN_SERVICE = "cabin";
    public static final String HVAC_SERVICE = "hvac";
    public static final String INFO_SERVICE = "info";
    public static final String SENSOR_SERVICE = "sensor";
    public static final String AUDIO_SERVICE = "audio";
    public static final String PERMISSION_CAR_POWERTRAIN = "android.car.permission.CAR_POWERTRAIN";

    // 车机系统 CarService 的固定组件名（不依赖包管理器查询）。
    private static final String CAR_SERVICE_PACKAGE = "com.android.car";
    private static final String CAR_SERVICE_CLASS = "com.android.car.CarService";

    private final Context context;
    private final ServiceConnection serviceConnectionListener;
    private final Handler handler;
    private final ComponentName componentName =
            new ComponentName(CAR_SERVICE_PACKAGE, CAR_SERVICE_CLASS);
    private final Object lock = new Object();

    private volatile boolean connecting;
    private volatile boolean bound;
    private volatile IBinder binder;
    private volatile CarPropertyManager propertyManager;

    /** 非 property 的车机服务管理器（cabin/sensor/hvac/...）按需创建并缓存。 */
    private final Map<String, Object> managerCache = new HashMap<String, Object>();

    private static final String TAG = "VirtualCar";

    /** AOSP / 厂商 Car.getCarManager 的 serviceName 到管理器类名的映射。 */
    private static final String[][] MANAGER_CLASSES = {
            {"cabin", "android.car.hardware.cabin.CarCabinManager"},
            {"hvac", "android.car.hardware.hvac.CarHvacManager"},
            {"sensor", "android.car.hardware.CarSensorManager"},
            {"audio", "android.car.media.CarAudioManager"},
            {"mcu_service", "android.car.hardware.mcu.CarMcuManager"},
            {"car_tbox_service", "android.car.hardware.tbox.CarTBoxManager"},
            {"info", "android.car.CarInfoManager"},
    };

    private final IBinder.DeathRecipient deathRecipient = new IBinder.DeathRecipient() {
        @Override
        public void binderDied() {
            handleDisconnected();
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            handleConnected(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            handleDisconnected();
        }
    };

    private Car(Context context, ServiceConnection serviceConnectionListener, Handler handler) {
        this.context = context.getApplicationContext();
        this.serviceConnectionListener = serviceConnectionListener;
        this.handler = handler != null ? handler : new Handler(Looper.getMainLooper());
    }

    public static Car createCar(Context context, ServiceConnection serviceConnectionListener) {
        return new Car(context, serviceConnectionListener, null);
    }

    public static Car createCar(Context context, ServiceConnection serviceConnectionListener,
            Handler handler) {
        return new Car(context, serviceConnectionListener, handler);
    }

    /**
     * 厂商 car-lib 管理器（CarDrivingStateManager 等，随 APK 打包、运行时不会被覆盖）
     * 会调用本静态方法把 CarService 抛出的 {@code IllegalStateException("CarNotConnected")}
     * 转成 {@link CarNotConnectedException}；缺此方法会在它们构造/回调时抛 NoSuchMethodError。
     */
    public static void checkCarNotConnectedExceptionFromCarService(IllegalStateException e)
            throws CarNotConnectedException {
        if (e != null && "CarNotConnected".equals(e.getMessage())) {
            throw new CarNotConnectedException();
        }
        throw e;
    }

    /**
     * 同上：厂商 car-lib 用它吞掉 "CarNotConnected" 异常（无返回值）。缺失会 NoSuchMethodError。
     */
    public static void hideCarNotConnectedExceptionFromCarService(IllegalStateException e) {
        if (e != null && "CarNotConnected".equals(e.getMessage())) {
            return;
        }
        throw e;
    }

    public void connect() {
        synchronized (lock) {
            if (binder != null || connecting) {
                return;
            }
            connecting = true;
        }
        try {
            Intent intent = new Intent();
            intent.setComponent(componentName);
            boolean accepted = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
            synchronized (lock) {
                if (!accepted) {
                    connecting = false;
                    bound = false;
                } else {
                    bound = true;
                }
            }
        } catch (Throwable ignored) {
            synchronized (lock) {
                connecting = false;
                bound = false;
            }
        }
    }

    public void disconnect() {
        IBinder current;
        synchronized (lock) {
            current = binder;
            binder = null;
            propertyManager = null;
            managerCache.clear();
            connecting = false;
            if (bound) {
                bound = false;
                try {
                    context.unbindService(connection);
                } catch (Throwable ignored) {
                }
            }
        }
        if (current != null) {
            try {
                current.unlinkToDeath(deathRecipient, 0);
            } catch (Throwable ignored) {
            }
        }
        notifyDisconnected();
    }

    public boolean isConnected() {
        IBinder current = binder;
        return current != null && current.isBinderAlive();
    }

    public boolean isConnecting() {
        return connecting;
    }

    public Object getCarManager(String serviceName) {
        if (serviceName == null) {
            return null;
        }
        if (PROPERTY_SERVICE.equals(serviceName)) {
            return propertyManager;
        }
        synchronized (lock) {
            Object cached = managerCache.get(serviceName);
            if (cached != null) {
                return cached;
            }
        }
        Object manager = createManager(serviceName);
        if (manager != null) {
            synchronized (lock) {
                managerCache.put(serviceName, manager);
            }
        }
        return manager;
    }

    /**
     * 用进程自己的 ClassLoader 加载 car-lib 管理器类并实例化。
     *
     * <p>厂商 APK 通常自带 car-lib 管理器（stub 或真实实现），bootclasspath 只覆盖
     * android.car.Car 本身；这里按 AOSP 构造参数 (IBinder, Context, Handler) 反射创建，
     * 避免 getCarManager 返回 null 导致厂商 onServiceConnected 直接 NPE。
     */
    private Object createManager(String serviceName) {
        String className = null;
        for (String[] entry : MANAGER_CLASSES) {
            if (entry[0].equals(serviceName)) {
                className = entry[1];
                break;
            }
        }
        if (className == null) {
            Log.w(TAG, "Car.getCarManager unsupported serviceName=" + serviceName);
            return null;
        }
        try {
            ClassLoader classLoader = context.getClassLoader();
            Class<?> managerClass = Class.forName(className, true, classLoader);
            IBinder serviceBinder = getCarServiceBinder(serviceName);
            try {
                Constructor<?> constructor = managerClass.getConstructor(
                        IBinder.class, Context.class, Handler.class);
                return constructor.newInstance(serviceBinder, context, handler);
            } catch (NoSuchMethodException ignored) {
                Constructor<?> constructor = managerClass.getConstructor(
                        IBinder.class, Context.class);
                return constructor.newInstance(serviceBinder, context);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Car.getCarManager " + serviceName + " -> " + className
                    + " failed: " + t);
            return null;
        }
    }

    /** 通过 ICar.getCarService(String)（事务码 2）向 CarService 申请子服务 binder。 */
    private IBinder getCarServiceBinder(String serviceName) {
        IBinder current = binder;
        if (current == null) {
            return null;
        }
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken("android.car.ICar");
            data.writeString(serviceName);
            current.transact(2, data, reply, 0);
            reply.readException();
            return reply.readStrongBinder();
        } catch (Throwable t) {
            Log.w(TAG, "getCarService(" + serviceName + ") failed: " + t);
            return null;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    public void onServiceConnected(IBinder service) {
        handleConnected(service);
    }

    public void onServiceDisconnected() {
        handleDisconnected();
    }

    private void handleConnected(IBinder service) {
        if (service == null) {
            return;
        }
        try {
            service.linkToDeath(deathRecipient, 0);
        } catch (RemoteException e) {
            return;
        }
        synchronized (lock) {
            binder = service;
            connecting = false;
            propertyManager = new CarPropertyManager(service);
        }
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (serviceConnectionListener != null) {
                    serviceConnectionListener.onServiceConnected(componentName, service);
                }
            }
        });
    }

    private void handleDisconnected() {
        boolean wasConnected;
        synchronized (lock) {
            wasConnected = binder != null;
            binder = null;
            propertyManager = null;
            managerCache.clear();
            connecting = false;
            bound = false;
        }
        if (wasConnected) {
            notifyDisconnected();
        }
    }

    private void notifyDisconnected() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (serviceConnectionListener != null) {
                    serviceConnectionListener.onServiceDisconnected(componentName);
                }
            }
        });
    }
}
