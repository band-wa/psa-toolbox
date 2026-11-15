package com.android.car;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;

import com.bandwa.vcarlib.BootCarProtocol;

import java.util.List;

/**
 * 冒充 AOSP CarService 的 Binder 服务端。
 *
 * <p>两类调用方共用同一个 Binder（服务声明 action {@code android.car.ICar}）：
 * <ul>
 *   <li>bootclasspath 版 android.car 客户端（{@link BootCarProtocol}）：事务码从 0x1000 起，
 *       直接读写 {@link CarState} 里的 20 个虚拟属性；</li>
 *   <li>系统里按 AOSP 方式使用 android.car 的车机应用（如 com.cusc.misc）：事务码是
 *       {@code android.car.ICar} AIDL 的 1/2/3（厂商实际布局，见 {@link #handleAospIcar}）。
 *       这里做最小兼容垫片：接受 setCarServiceHelper，getCarService 按名字返回
 *       {@link VendorServiceShims} 里的 ICarAudio / ICarProperty / 通用 Binder，
 *       getCarConnectionType 返回嵌入式类型。</li>
 * </ul>
 *
 * <p>分界常量 0x1000 保证 AOSP 小号事务码永远不会被当成自定义协议解析：否则 1/2/3 会与
 * TX_GET/TX_SET/TX_REGISTER 冲突，系统车机应用收到畸形 reply 后抛 SecurityException。
 */
public class CarService extends Service {
    /** 自定义协议事务码起点；小于它的一律走 AOSP ICar 兼容垫片。 */
    private static final int LOCAL_TX_BASE = 0x1000;

    /** AOSP {@code Car.CONNECTION_TYPE_EMBEDDED}：与真车嵌入式实现一致。 */
    private static final int CAR_CONNECTION_TYPE_EMBEDDED = 5;

    private final CarState state = CarState.get();

    private final IBinder binder = new Binder() {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code >= LOCAL_TX_BASE) {
                return handleLocalTransaction(code, data, reply);
            }
            return handleAospIcar(code, data, reply);
        }
    };

    // ---------------------------------------------------------------------
    // 自定义协议（BootCarProtocol，事务码 >= 0x1000）
    // ---------------------------------------------------------------------

    private boolean handleLocalTransaction(int code, Parcel data, Parcel reply)
            throws RemoteException {
        switch (code) {
            case BootCarProtocol.TX_GET:
                handleGet(data, reply);
                return true;
            case BootCarProtocol.TX_SET:
                handleSet(data, reply);
                return true;
            case BootCarProtocol.TX_REGISTER:
                handleRegister(data, reply);
                return true;
            case BootCarProtocol.TX_UNREGISTER:
                handleUnregister(data);
                return true;
            case BootCarProtocol.TX_GET_CONFIGS:
                handleGetConfigs(reply);
                return true;
            case BootCarProtocol.TX_PING:
                reply.writeInt(1);
                return true;
            default:
                // 未知自定义事务：不写数据、不抛异常，避免客户端 readException 失败。
                if (reply != null) {
                    reply.writeNoException();
                }
                return true;
        }
    }

    // ---------------------------------------------------------------------
    // AOSP android.car.ICar 兼容垫片（事务码 < 0x1000）
    // ---------------------------------------------------------------------

    /**
     * 厂商车机应用调用的 AOSP ICar 事务码（来自 verify/vendor_icar_proxy.smali 实证）：
     *
     * <pre>
     *   1 oneway setCarServiceHelper(IBinder helper)
     *   2 IBinder getCarService(String name)
     *   3 int getCarConnectionType()
     * </pre>
     *
     * <p>这里不再猜测 AOSP 8/9/10 的版本差异：厂商 APK 用的是固定的 1/2/3 布局，
     * 每个 code 按真实语义处理；其它小号事务码（7.x register/unregister、10.x
     * setUserLockStatus 等）一律只回 no-exception，保证 binder 调用链存活。
     *
     * <p>所有写 reply 的地方都先判空（oneway 事务 reply 为 null）。
     */
    private boolean handleAospIcar(int code, Parcel data, Parcel reply) {
        // AIDL 请求前缀：int strictModePolicy + String interface descriptor，
        // 与 enforceInterface() 的读取顺序一致；读不到/读少都不影响后续处理。
        try {
            data.readInt();
            data.readString();
        } catch (Throwable ignored) {
            // 非 AIDL 调用方没有 token，正常跳过。
        }

        switch (code) {
            case 1:
                // oneway setCarServiceHelper(IBinder helper)：读掉参数即可，helper 用不上。
                try {
                    data.readStrongBinder();
                } catch (Throwable ignored) {
                }
                if (reply != null) {
                    reply.writeNoException();
                }
                return true;
            case 2: {
                // IBinder getCarService(String name)：按名字返回 ICarAudio/ICarProperty 垫片。
                String name = null;
                try {
                    name = data.readString();
                } catch (Throwable ignored) {
                }
                Log.i(CarState.TAG, "ICar getCarService name=" + name);
                if (reply != null) {
                    reply.writeNoException();
                    reply.writeStrongBinder(VendorServiceShims.serviceFor(name));
                }
                return true;
            }
            case 3:
                // int getCarConnectionType()：返回嵌入式车机连接类型。
                if (reply != null) {
                    reply.writeNoException();
                    reply.writeInt(CAR_CONNECTION_TYPE_EMBEDDED);
                }
                return true;
            default:
                // 其它 AOSP 小号事务码：不写数据、不抛异常，保持 binder 调用链存活。
                if (reply != null) {
                    reply.writeNoException();
                }
                return true;
        }
    }

    // ---------------------------------------------------------------------
    // 生命周期
    // ---------------------------------------------------------------------

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(CarState.TAG, "CarService created");
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(CarState.TAG, "CarService bound by " + (intent == null ? null
                : intent.getComponent()) + " action=" + (intent == null ? null
                : intent.getAction()));
        return binder;
    }

    // ---------------------------------------------------------------------
    // 自定义协议的处理函数
    // ---------------------------------------------------------------------

    private void handleGet(Parcel data, Parcel reply) {
        int propId = data.readInt();
        int areaId = data.readInt();
        CarState.Property property = state.property(propId);
        if (property == null) {
            reply.writeInt(BootCarProtocol.RESULT_NOT_FOUND);
            return;
        }
        reply.writeInt(BootCarProtocol.RESULT_OK);
        reply.writeInt(property.type);
        reply.writeLong(android.os.SystemClock.uptimeMillis());
        reply.writeInt(BootCarProtocol.STATUS_AVAILABLE);
        BootCarProtocol.writeValue(reply, property.type, property.value);
    }

    private void handleSet(Parcel data, Parcel reply) {
        int propId = data.readInt();
        int areaId = data.readInt();
        int type = data.readInt();
        Object value = BootCarProtocol.readValue(data, type);
        int result = state.write(propId, value);
        reply.writeInt(result);
        Log.i(CarState.TAG, "set 0x" + Integer.toHexString(propId) + " area=" + areaId + " -> "
                + result);
    }

    private void handleRegister(Parcel data, Parcel reply) {
        int propId = data.readInt();
        float rate = data.readFloat();
        IBinder callback = data.readStrongBinder();
        boolean registered = state.register(propId, rate, callback);
        reply.writeInt(registered ? BootCarProtocol.RESULT_OK
                : BootCarProtocol.RESULT_NOT_FOUND);
    }

    private void handleUnregister(Parcel data) {
        int propId = data.readInt();
        IBinder callback = data.readStrongBinder();
        state.unregister(propId, callback);
    }

    private void handleGetConfigs(Parcel reply) {
        List<CarState.Property> properties = state.properties();
        reply.writeInt(properties.size());
        for (CarState.Property property : properties) {
            reply.writeInt(property.id);
            reply.writeInt(property.type);
            reply.writeInt(property.changeMode);
            reply.writeInt(property.writable ? 1 : 0);
            reply.writeFloat(property.minValue);
            reply.writeFloat(property.maxValue);
            reply.writeInt(property.areaIds.length);
            for (int areaId : property.areaIds) {
                reply.writeInt(areaId);
            }
        }
    }

    @Override
    public void onDestroy() {
        Log.i(CarState.TAG, "CarService destroyed");
        super.onDestroy();
    }
}
