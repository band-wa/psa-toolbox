package com.android.car;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;

import com.bandwa.vcarlib.BootCarProtocol;

/**
 * 给系统车机应用（如 com.cusc.misc）用的 AOSP 服务端兼容垫片。
 *
 * <p>厂商的 {@code ICar} proxy 事务码布局（由 verify/vendor_icar_proxy.smali 实证）是：
 * <pre>
 *   1 oneway setCarServiceHelper(IBinder)
 *   2 IBinder getCarService(String name)
 *   3 int getCarConnectionType()
 * </pre>
 * {@code getCarService(name)} 返回的 Binder 会被分别当作 {@code ICarAudio} /
 * {@code ICarProperty} 使用，事务码表与参数来自厂商 APK（见 verify/vendor_icaraudio_proxy.smali、
 * verify/vendor_icarproperty_proxy.smali、verify/jadx/ICarAudioProxy.java、ICarPropertyProxy.java）。
 *
 * <p>ICarProperty 部分按厂商 {@code com.android.car.manager.CarPropertyManager} 实际读取的属性 ID
 * 返回 {@code CarPropertyValue}（编码与 bootclasspath 里的
 * {@code android.car.hardware.CarPropertyValue.CREATOR} 一一对应，见 api/src 的实现）；未列出的
 * ID 回退到 CarState（若存在该虚拟属性）或整数 0。所有 reply 可能为 null（oneway 事务），
 * 写回前必须判空；任何异常都在内部吞掉，保证厂商 binder 调用链不会因 SecurityException /
 * 越界读 / ClassCastException 而 FATAL。
 */
final class VendorServiceShims {
    private static final String TAG = CarState.TAG;

    // ---- android.car.media.ICarAudio 事务码（vendor_icaraudio_proxy.smali / ICarAudioProxy.java）----
    private static final int AUDIO_SET_GROUP_VOLUME = 0x01;
    private static final int AUDIO_GET_GROUP_MAX_VOLUME = 0x02;
    private static final int AUDIO_GET_GROUP_MIN_VOLUME = 0x03;
    private static final int AUDIO_GET_GROUP_VOLUME = 0x04;
    private static final int AUDIO_SET_FADE_TOWARD_FRONT = 0x05;
    private static final int AUDIO_GET_FADE_TOWARD_FRONT = 0x06;
    private static final int AUDIO_SET_BALANCE_TOWARD_RIGHT = 0x07;
    private static final int AUDIO_GET_BALANCE_TOWARD_RIGHT = 0x08;
    private static final int AUDIO_SET_BAL_FAD_BALANCE = 0x09;
    private static final int AUDIO_GET_EXTERNAL_SOURCES = 0x0a;
    private static final int AUDIO_CREATE_AUDIO_PATCH = 0x0b;
    private static final int AUDIO_RELEASE_AUDIO_PATCH = 0x0c;
    private static final int AUDIO_GET_VOLUME_GROUP_COUNT = 0x0d;
    private static final int AUDIO_GET_VOLUME_GROUP_ID_FOR_USAGE = 0x0e;
    private static final int AUDIO_GET_USAGES_FOR_VOLUME_GROUP_ID = 0x0f;
    private static final int AUDIO_REGISTER_VOLUME_CALLBACK = 0x10;
    private static final int AUDIO_UNREGISTER_VOLUME_CALLBACK = 0x11;
    private static final int AUDIO_SET_MIC_MODE = 0x12;
    private static final int AUDIO_SET_BEEP_LEVEL = 0x13;
    private static final int AUDIO_GET_BEEP_LEVEL = 0x14;
    private static final int AUDIO_SET_MASTER_MUTE = 0x15;
    private static final int AUDIO_SET_AVC_LEVEL = 0x16;
    private static final int AUDIO_GET_AVC_LEVEL = 0x17;
    private static final int AUDIO_SET_EQ_MODE = 0x18;
    private static final int AUDIO_GET_EQ_MODE = 0x19;
    private static final int AUDIO_SET_AUDIO_HIGH_VOICE = 0x1a;
    private static final int AUDIO_GET_AUDIO_HIGH_VOICE = 0x1b;
    private static final int AUDIO_SET_AUDIO_MID_VOICE = 0x1c;
    private static final int AUDIO_GET_AUDIO_MID_VOICE = 0x1d;
    private static final int AUDIO_SET_AUDIO_LOW_VOICE = 0x1e;
    private static final int AUDIO_GET_AUDIO_LOW_VOICE = 0x1f;
    private static final int AUDIO_GET_CURRENT_USAGE = 0x20;
    private static final int AUDIO_SWITCH_MIC = 0x21;
    private static final int AUDIO_ENABLE_SPEED_GAIN = 0x22;
    private static final int AUDIO_DISABLE_SPEED_GAIN = 0x23;
    private static final int AUDIO_SET_LOUDNESS = 0x24;
    private static final int AUDIO_GET_LOUDNESS = 0x25;
    private static final int AUDIO_SET_VOLUME_WITH_SPEED = 0x26;
    private static final int AUDIO_GET_VOLUME_WITH_SPEED = 0x27;

    // ---- android.car.hardware.property.ICarProperty 事务码 ----
    private static final int PROPERTY_REGISTER_LISTENER = 1;    private static final int PROPERTY_UNREGISTER_LISTENER = 2;
    private static final int PROPERTY_GET_PROPERTY_LIST = 3;
    private static final int PROPERTY_GET_PROPERTY = 4;
    private static final int PROPERTY_SET_PROPERTY = 5;

    /**
     * 自定义协议（BootCarProtocol）事务码起点：bootclasspath 的 api 类会以 0x1001 等事务码
     * 访问同一个 ICarProperty binder，必须与 AOSP AIDL 事务码（0..5）分流处理。
     */
    private static final int CUSTOM_TX_BASE = 0x1000;

    // ---- 厂商 CarPropertyManager 读取、但不在 CarState 里的属性 ID ----
    /** MCU 版本（getMcuVersion，String）。 */
    private static final int PROP_MCU_VERSION = 0x2110F056;
    /** DOTE 配置（getDoteConfig 读成 byte[]，byte[16]==1 表示 AVM 已配置）。 */
    private static final int PROP_DOTE_CONFIG = 0x2170F003;
    /** 夜间/大灯模式（getHeadlightsMode，Integer）。 */
    private static final int PROP_NIGHT_MODE = 0x2140F05C;
    /** 仪表个性化信息（getInstrumentDisplayAvailable 读成 Integer[8]）。 */
    private static final int PROP_IP_INDIV_INFO = 0x2141F30C;
    /** 仪表语言（getInstrumentLanguage，area 0x75，Integer：1=EN 2=ZH）。 */
    private static final int PROP_LANGUAGE = 0x2140F308;
    /** 语言切换完成标志（setInstrumentLanguage 写 2 再写 1）。 */
    private static final int PROP_LANGUAGE_OK = 0x2140F303;
    /** 保养天数（getMaintenanceDay，Integer）。 */
    private static final int PROP_MAINTENANCE_DAY = 0x2140F526;
    /** 保养里程（getMaintenanceMileage，Integer）。 */
    private static final int PROP_MAINTENANCE_MILEAGE = 0x2140F336;
    /** 保养到期标志（isMaintenanceExpired，Integer：2=到期）。 */
    private static final int PROP_SIGN_MAINT = 0x2140F339;
    /** 仪表亮度（setInstrumentBrightness 写入 brightness+1）。 */
    private static final int PROP_BRIGHTNESS = 0x2140F300;
    /** 仪表显示左侧/右侧（setInstrumentDisplay 写 area 1/4）。 */
    private static final int PROP_DASHBOARD = 0x2140F301;
    /** 仪表显示刷新完成标志（setInstrumentDisplay 写 2 再写 1）。 */
    private static final int PROP_DASHBOARD_OK = 0x2140F302;
    /** DSP 静音（disableDSP 写 Integer(1)）。 */
    private static final int PROP_DSP_MUTE = 0x2140F01B;
    /** ACC 状态（事件属性，仅注册监听）。 */
    private static final int PROP_ACC_STATE = 0x2140F0E2;
    /** 休眠状态（CanMcuEmitter 注册监听）。 */
    private static final int PROP_SLEEP_STATE = 0x2140F3FD;
    /** ECO 状态（CanMcuEmitter 注册监听）。 */
    private static final int PROP_ECO_STATE = 0x2140F321;

    /** 虚拟音量组数量：1（与 getVolumeGroupIdForUsage 恒返回 0 保持一致）。 */
    private static final int VOLUME_GROUP_COUNT = 1;
    /** 虚拟音量组最大音量（AOSP 习惯值）。 */
    private static final int GROUP_MAX_VOLUME = 30;
    /** 虚拟音量组当前音量。 */
    private static final int GROUP_VOLUME = 20;

    private VendorServiceShims() {
    }

    static IBinder serviceFor(String name) {
        if ("audio".equals(name)) {
            return AUDIO;
        }
        if ("property".equals(name) || "cabin".equals(name) || "mcu_service".equals(name)
                || "sensor".equals(name) || "car_tbox_service".equals(name)
                || "hvac".equals(name) || "info".equals(name)) {
            // 厂商把 getCarService("cabin") 等拿到的 binder 交给 AOSP car-lib 管理器，
            // 管理器内部再以 ICarProperty 事务码（registerListener/getProperty/...）
            // 或本项目的自定义协议（>=0x1000）访问属性，因此统一用 PROPERTY 垫片。
            return PROPERTY;
        }
        Log.i(TAG, "ICar getCarService name=" + name + " -> generic shim");
        return GENERIC;
    }

    /** getCarService("audio")：ICarAudio 兼容垫片，回默认音量参数。 */
    private static final IBinder AUDIO = new Binder() {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            try {
                switch (code) {
                    case AUDIO_GET_GROUP_MAX_VOLUME:
                        return writeIntResult(reply, GROUP_MAX_VOLUME);
                    case AUDIO_GET_GROUP_MIN_VOLUME:
                        return writeIntResult(reply, 0);
                    case AUDIO_GET_GROUP_VOLUME:
                        return writeIntResult(reply, GROUP_VOLUME);
                    case AUDIO_GET_VOLUME_GROUP_COUNT:
                        return writeIntResult(reply, VOLUME_GROUP_COUNT);
                    case AUDIO_GET_VOLUME_GROUP_ID_FOR_USAGE:
                        // 只有 1 个虚拟音量组，所有 usage 都归到组 0。
                        return writeIntResult(reply, 0);
                    case AUDIO_GET_EXTERNAL_SOURCES:
                        // 厂商 CarAudioManager 不调用；给空数组而不是 null，避免调用方 NPE。
                        return writeStringArrayResult(reply, new String[0]);
                    case AUDIO_GET_USAGES_FOR_VOLUME_GROUP_ID:
                        return writeIntArrayResult(reply, new int[0]);
                    case AUDIO_CREATE_AUDIO_PATCH:
                        // 0 == null CarAudioPatchHandle（AIDL 的“可空对象”标志）。
                        return writeIntResult(reply, 0);
                    case AUDIO_GET_FADE_TOWARD_FRONT:
                    case AUDIO_GET_BALANCE_TOWARD_RIGHT:
                    case AUDIO_GET_BEEP_LEVEL:
                    case AUDIO_GET_AVC_LEVEL:
                    case AUDIO_GET_EQ_MODE:
                    case AUDIO_GET_AUDIO_HIGH_VOICE:
                    case AUDIO_GET_AUDIO_MID_VOICE:
                    case AUDIO_GET_AUDIO_LOW_VOICE:
                    case AUDIO_GET_CURRENT_USAGE:
                        return writeIntResult(reply, 0);
                    case AUDIO_GET_LOUDNESS:
                    case AUDIO_GET_VOLUME_WITH_SPEED:
                        return writeBoolResult(reply, false);
                    case AUDIO_REGISTER_VOLUME_CALLBACK:
                        Log.i(TAG, "ICarAudio registerVolumeCallback (shim)");
                        return writeNoException(reply);
                    case AUDIO_UNREGISTER_VOLUME_CALLBACK:
                        return writeNoException(reply);
                    default:
                        // setXxx / enable/disable 之类 void 事务：只需要 no-exception。
                        return writeNoException(reply);
                }
            } catch (Throwable t) {
                Log.w(TAG, "ICarAudio shim transact " + code + " error: " + t);
                return writeNoException(reply);
            }
        }
    };

    /** getCarService("property"/"cabin"/...)：属性垫片，同时服务 AIDL 与自定义协议两种事务码。 */
    private static final IBinder PROPERTY = new Binder() {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            try {
                if (code >= CUSTOM_TX_BASE) {
                    // bootclasspath 里的 api 类（AOSP 管理器内部创建的 CarPropertyManager）
                    // 走本项目的自定义协议；与 AOSP AIDL 事务码（1..5）严格分流。
                    return handleCustomLocal(code, data, reply);
                }
                switch (code) {
                    case PROPERTY_REGISTER_LISTENER:
                        return handleRegisterListener(data, reply);
                    case PROPERTY_UNREGISTER_LISTENER:
                        return handleUnregisterListener(data, reply);
                    case PROPERTY_GET_PROPERTY_LIST:
                        // createTypedArrayList(CarPropertyConfig.CREATOR)：0 == 空列表。
                        Log.i(TAG, "ICarProperty getPropertyList (shim: empty)");
                        return writeIntResult(reply, 0);
                    case PROPERTY_GET_PROPERTY:
                        return handleGetProperty(data, reply);
                    case PROPERTY_SET_PROPERTY:
                        return handleSetProperty(data, reply);
                    default:
                        return writeNoException(reply);
                }
            } catch (Throwable t) {
                Log.w(TAG, "ICarProperty shim transact " + code + " error: " + t);
                return writeNoException(reply);
            }
        }
    };

    /** 其它 getCarService(name)：任何事务都只回 no-exception。 */
    private static final IBinder GENERIC = new Binder() {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (reply != null) {
                reply.writeNoException();
            }
            return true;
        }
    };

    // ---- ICarProperty 事务处理 ----

    private static boolean handleRegisterListener(Parcel data, Parcel reply) {
        consumeInterfaceToken(data);
        int propId = readIntOr(data, 0);
        float rate = readFloatOr(data, 0f);
        Log.i(TAG, "ICarProperty registerListener propId=0x" + Integer.toHexString(propId)
                + " rate=" + rate + " (shim)");
        return writeNoException(reply);
    }

    private static boolean handleUnregisterListener(Parcel data, Parcel reply) {
        consumeInterfaceToken(data);
        int propId = readIntOr(data, 0);
        Log.i(TAG, "ICarProperty unregisterListener propId=0x" + Integer.toHexString(propId));
        return writeNoException(reply);
    }

    private static boolean handleGetProperty(Parcel data, Parcel reply) {
        consumeInterfaceToken(data);
        int propId = readIntOr(data, 0);
        int areaId = readIntOr(data, 0);
        if (reply == null) {
            return true;
        }
        Object value = vendorPropertyValue(propId);
        Log.i(TAG, "ICarProperty getProperty propId=0x" + Integer.toHexString(propId)
                + " area=" + areaId + " -> " + value);
        reply.writeNoException();
        if (value == null) {
            reply.writeInt(0);
            return true;
        }
        int type = BootCarProtocol.typeOf(value);
        reply.writeInt(1);
        reply.writeInt(propId);
        reply.writeInt(areaId);
        reply.writeInt(BootCarProtocol.STATUS_AVAILABLE);
        reply.writeLong(SystemClock.uptimeMillis());
        reply.writeInt(type);
        BootCarProtocol.writeValue(reply, type, value);
        return true;
    }

    private static boolean handleSetProperty(Parcel data, Parcel reply) {
        consumeInterfaceToken(data);
        int nonNull = readIntOr(data, 0);
        if (nonNull != 0) {
            int propId = readIntOr(data, 0);
            int areaId = readIntOr(data, 0);
            int status = readIntOr(data, 0);
            long timestamp = readLongOr(data, 0L);
            int type = readIntOr(data, BootCarProtocol.TYPE_NULL);
            Object value = null;
            try {
                value = BootCarProtocol.readValue(data, type);
            } catch (Throwable t) {
                Log.w(TAG, "ICarProperty setProperty 0x" + Integer.toHexString(propId)
                        + " decode error: " + t);
            }
            Log.i(TAG, "ICarProperty setProperty propId=0x" + Integer.toHexString(propId)
                    + " area=" + areaId + " status=" + status + " ts=" + timestamp
                    + " type=" + type + " value=" + value);
            applyProperty(propId, value);
        }
        return writeNoException(reply);
    }

    /** 把厂商 setProperty 写进 CarState（类型不匹配/未知属性只记日志，不抛异常）。 */
    private static void applyProperty(int propId, Object value) {
        applyPropertyResult(propId, value);
    }

    /**
     * 把 setProperty 写进 CarState 并返回 BootCarProtocol 结果码。
     *
     * <p>未知属性返回 RESULT_NOT_FOUND，让 api 侧 getter/setter 走安全兜底，
     * 不要伪造误导性数据。
     */
    private static int applyPropertyResult(int propId, Object value) {
        try {
            CarState state = CarState.get();
            int stateType = state.typeOf(propId);
            if (stateType == BootCarProtocol.TYPE_NULL) {
                Log.i(TAG, "ICarProperty setProperty 0x" + Integer.toHexString(propId)
                        + " ignored (not a virtual property)");
                return BootCarProtocol.RESULT_NOT_FOUND;
            }
            Object coerced = BootCarProtocol.coerce(stateType, value);
            if (coerced == null) {
                Log.w(TAG, "ICarProperty setProperty 0x" + Integer.toHexString(propId)
                        + " type mismatch: value=" + value);
                return BootCarProtocol.RESULT_TYPE_MISMATCH;
            }
            int result = state.forceWrite(propId, coerced);
            Log.i(TAG, "ICarProperty setProperty 0x" + Integer.toHexString(propId)
                    + " applied value=" + coerced + " result=" + result);
            return result;
        } catch (Throwable t) {
            Log.w(TAG, "ICarProperty setProperty 0x" + Integer.toHexString(propId)
                    + " apply error: " + t);
            return BootCarProtocol.RESULT_NOT_FOUND;
        }
    }

    // ---- 自定义协议（CUSTOM_TX_BASE 起）事务处理 ----

    /**
     * 处理 bootclasspath api 类的自定义协议事务。
     *
     * <p>请求/应答格式必须与 api/src/.../CarPropertyManager.java 完全一致。注意自定义协议
     * **不带 AIDL 的 exception 前缀**（api 的 {@code transact()} 直接读业务字段，
     * {@code getProperty} 第一项读 result、{@code getPropertyList} 第一项读 count）：
     * TX_GET 应答 = result + type + timestamp + status + value；
     * TX_SET 应答 = result；TX_GET_CONFIGS 应答 = count；TX_REGISTER 应答 = result。
     * 只有 code&lt;0x1000 的 AOSP AIDL 路径才需要 {@code writeNoException()}。
     */
    private static boolean handleCustomLocal(int code, Parcel data, Parcel reply) {
        switch (code) {
            case BootCarProtocol.TX_GET:
                return handleCustomGet(data, reply);
            case BootCarProtocol.TX_SET:
                return handleCustomSet(data, reply);
            case BootCarProtocol.TX_REGISTER:
                return handleCustomRegister(data, reply);
            case BootCarProtocol.TX_UNREGISTER:
                return handleCustomUnregister(data, reply);
            case BootCarProtocol.TX_GET_CONFIGS:
                // createTypedArrayList(CarPropertyConfig.CREATOR)：0 == 空列表。
                return writeCustomInt(reply, 0);
            case BootCarProtocol.TX_PING:
            default:
                return writeCustomInt(reply, BootCarProtocol.RESULT_OK);
        }
    }

    private static boolean handleCustomGet(Parcel data, Parcel reply) {
        int propId = readIntOr(data, 0);
        int areaId = readIntOr(data, 0);
        Object value = customPropertyValue(propId);
        Log.i(TAG, "custom TX_GET propId=0x" + Integer.toHexString(propId) + " area=" + areaId
                + " -> " + value);
        if (reply == null) {
            return true;
        }
        if (value == null) {
            reply.writeInt(BootCarProtocol.RESULT_NOT_FOUND);
            reply.writeInt(BootCarProtocol.TYPE_NULL);
            reply.writeLong(SystemClock.uptimeMillis());
            reply.writeInt(BootCarProtocol.STATUS_UNAVAILABLE);
            return true;
        }
        int type = BootCarProtocol.typeOf(value);
        reply.writeInt(BootCarProtocol.RESULT_OK);
        reply.writeInt(type);
        reply.writeLong(SystemClock.uptimeMillis());
        reply.writeInt(BootCarProtocol.STATUS_AVAILABLE);
        BootCarProtocol.writeValue(reply, type, value);
        return true;
    }

    private static boolean handleCustomSet(Parcel data, Parcel reply) {
        int propId = readIntOr(data, 0);
        int areaId = readIntOr(data, 0);
        int type = readIntOr(data, BootCarProtocol.TYPE_NULL);
        Object value = null;
        try {
            value = BootCarProtocol.readValue(data, type);
        } catch (Throwable t) {
            Log.w(TAG, "custom TX_SET 0x" + Integer.toHexString(propId) + " decode error: " + t);
        }
        Log.i(TAG, "custom TX_SET propId=0x" + Integer.toHexString(propId) + " area=" + areaId
                + " type=" + type + " value=" + value);
        int result = applyPropertyResult(propId, value);
        return writeCustomInt(reply, result);
    }

    private static boolean handleCustomRegister(Parcel data, Parcel reply) {
        int propId = readIntOr(data, 0);
        float rate = readFloatOr(data, 0f);
        Log.i(TAG, "custom TX_REGISTER propId=0x" + Integer.toHexString(propId) + " rate=" + rate);
        return writeCustomInt(reply, BootCarProtocol.RESULT_OK);
    }

    private static boolean handleCustomUnregister(Parcel data, Parcel reply) {
        int propId = readIntOr(data, 0);
        Log.i(TAG, "custom TX_UNREGISTER propId=0x" + Integer.toHexString(propId));
        return writeCustomInt(reply, BootCarProtocol.RESULT_OK);
    }

    /**
     * 自定义协议读取的属性值：CarState 里存在的虚拟属性返回真实值；厂商专用 ID 返回
     * 与 AIDL 路径一致的虚拟值；完全未知的返回 null（api 侧兜底为空/0/false）。
     */
    private static Object customPropertyValue(int propId) {
        switch (propId) {
            case PROP_DOTE_CONFIG: {
                byte[] config = new byte[100];
                config[16] = 1;
                return config;
            }
            case PROP_MCU_VERSION:
                return "VirtualCar-1.0.0";
            case PROP_NIGHT_MODE:
                return 0;
            case PROP_IP_INDIV_INFO:
                return new Integer[] {1, 1, 1, 1, 1, 1, 1, 1};
            case PROP_LANGUAGE:
                return 2;
            case PROP_LANGUAGE_OK:
            case PROP_MAINTENANCE_DAY:
            case PROP_MAINTENANCE_MILEAGE:
            case PROP_SIGN_MAINT:
            case PROP_BRIGHTNESS:
            case PROP_DASHBOARD:
            case PROP_DASHBOARD_OK:
            case PROP_DSP_MUTE:
            case PROP_ACC_STATE:
            case PROP_SLEEP_STATE:
            case PROP_ECO_STATE:
                return 0;
            case CarState.PROP_AVM_SWITCH:
                return new Integer[] {0, 0};
            default:
                return stateValue(propId);
        }
    }

    // ---- 属性值映射（类型必须与厂商 getValue() 后的 check-cast 一致）----

    private static Object vendorPropertyValue(int propId) {
        if (propId == CarState.PROP_VIN) {
            Object vin = stateValue(propId);
            return vin != null ? vin : "VF3XXXXXXXXXXXXXX";
        }
        Object value = customPropertyValue(propId);
        if (value != null) {
            return value;
        }
        Log.i(TAG, "ICarProperty getProperty 0x" + Integer.toHexString(propId)
                + " unknown -> 0");
        return 0;
    }

    private static Object stateValue(int propId) {
        try {
            CarState.Property property = CarState.get().property(propId);
            return property == null ? null : property.value;
        } catch (Throwable t) {
            return null;
        }
    }

    // ---- parcel 小工具 ----

    /**
     * 消费 AIDL 接口 token：writeInterfaceToken 先写 int（strictModePolicy）再写 String
     * （interface descriptor，enforceInterface 会先 readInt 再 readString）。失败则忽略，
     * 后续读取都由 readXxxOr 兜底，不会越界抛异常。
     */
    private static void consumeInterfaceToken(Parcel data) {
        try {
            data.readInt();
            data.readString();
        } catch (Throwable ignored) {
            // 空/畸形 data：后续字段读取按缺省值处理。
        }
    }

    private static int readIntOr(Parcel data, int defaultValue) {
        try {
            return data.readInt();
        } catch (Throwable t) {
            return defaultValue;
        }
    }

    private static float readFloatOr(Parcel data, float defaultValue) {
        try {
            return data.readFloat();
        } catch (Throwable t) {
            return defaultValue;
        }
    }

    private static long readLongOr(Parcel data, long defaultValue) {
        try {
            return data.readLong();
        } catch (Throwable t) {
            return defaultValue;
        }
    }

    private static boolean writeNoException(Parcel reply) {
        if (reply != null) {
            reply.writeNoException();
        }
        return true;
    }

    private static boolean writeIntResult(Parcel reply, int value) {
        if (reply != null) {
            reply.writeNoException();
            reply.writeInt(value);
        }
        return true;
    }

    /**
     * 自定义协议（code &gt;= 0x1000）的 int 应答：与 api 的 {@code transact()} 对齐，
     * **不写 AIDL exception 前缀**，直接写业务 int。
     */
    private static boolean writeCustomInt(Parcel reply, int value) {
        if (reply != null) {
            reply.writeInt(value);
        }
        return true;
    }

    private static boolean writeBoolResult(Parcel reply, boolean value) {
        return writeIntResult(reply, value ? 1 : 0);
    }

    private static boolean writeIntArrayResult(Parcel reply, int[] value) {
        if (reply != null) {
            reply.writeNoException();
            reply.writeIntArray(value);
        }
        return true;
    }

    private static boolean writeStringArrayResult(Parcel reply, String[] value) {
        if (reply != null) {
            reply.writeNoException();
            reply.writeStringArray(value);
        }
        return true;
    }
}
