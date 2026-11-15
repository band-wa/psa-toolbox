package com.bandwa.vcarlib;

import android.os.Parcel;

/**
 * bootclasspath 版 android.car 客户端与冒充 com.android.car 的服务端之间的共享 Binder 协议。
 *
 * <p>事务码从 0x1000 起，与 AOSP {@code android.car.ICar} 的 AIDL 事务码（0..4）严格隔离：
 * 系统里的车机应用（如 com.cusc.misc）会按 AOSP 方式向同一个 Binder 服务端发 1/2/3 等小号
 * 事务码，服务端必须把它们交给 ICar 兼容垫片处理，绝不能当成自定义协议解析。
 *
 * <p>客户端 jar 与服务端 APK 各自编译这一份共享源码，升级事务码时两边必须成对更新。
 */
public final class BootCarProtocol {
    private BootCarProtocol() {
    }

    public static final String SERVER_PACKAGE = "com.android.car";
    public static final String SERVER_SERVICE = "com.android.car.CarService";
    public static final String ACTION_CONTROL = "com.android.car.CONTROL";

    public static final int TX_GET = 0x1001;
    public static final int TX_SET = 0x1002;
    public static final int TX_REGISTER = 0x1003;
    public static final int TX_UNREGISTER = 0x1004;
    public static final int TX_GET_CONFIGS = 0x1005;
    public static final int TX_PING = 0x1006;
    public static final int TX_EVENT = 0x1100;

    public static final int RESULT_OK = 0;
    public static final int RESULT_NOT_FOUND = -1;
    public static final int RESULT_NOT_WRITABLE = -2;
    public static final int RESULT_TYPE_MISMATCH = -3;

    public static final int TYPE_NULL = 0;
    public static final int TYPE_INT = 1;
    public static final int TYPE_FLOAT = 2;
    public static final int TYPE_BOOL = 3;
    public static final int TYPE_STRING = 4;
    public static final int TYPE_INT_ARRAY = 5;
    /**
     * byte[] 与 Integer[] 只用于 CarPropertyValue 的 Parcel 编解码（AOSP 属性类型里有
     * byte[] 配置、以及厂商用 Integer[] 表示仪表/AVM 的枚举数组）。自定义协议的类型码
     * 仍然只用 0..5，新增码不会与既有语义冲突。
     */
    public static final int TYPE_BYTE_ARRAY = 6;
    public static final int TYPE_INTEGER_ARRAY = 7;
    /** float[] 同样只用于 AOSP 属性的浮点数组类型（CarPropertyManager#getFloatArrayProperty）。 */
    public static final int TYPE_FLOAT_ARRAY = 8;

    public static final int STATUS_AVAILABLE = 0;
    public static final int STATUS_UNAVAILABLE = 1;

    public static int typeOf(Object value) {
        if (value instanceof Integer) {
            return TYPE_INT;
        }
        if (value instanceof Float) {
            return TYPE_FLOAT;
        }
        if (value instanceof Boolean) {
            return TYPE_BOOL;
        }
        if (value instanceof String) {
            return TYPE_STRING;
        }
        if (value instanceof byte[]) {
            return TYPE_BYTE_ARRAY;
        }
        if (value instanceof Integer[]) {
            return TYPE_INTEGER_ARRAY;
        }
        if (value instanceof int[]) {
            return TYPE_INT_ARRAY;
        }
        if (value instanceof float[]) {
            return TYPE_FLOAT_ARRAY;
        }
        return TYPE_NULL;
    }

    public static Object coerce(int type, Object value) {
        if (value == null) {
            return null;
        }
        switch (type) {
            case TYPE_INT:
                if (value instanceof Number) {
                    return ((Number) value).intValue();
                }
                if (value instanceof Boolean) {
                    return ((Boolean) value) ? 1 : 0;
                }
                return null;
            case TYPE_FLOAT:
                if (value instanceof Number) {
                    return ((Number) value).floatValue();
                }
                return null;
            case TYPE_BOOL:
                if (value instanceof Boolean) {
                    return value;
                }
                if (value instanceof Number) {
                    return ((Number) value).intValue() != 0;
                }
                return null;
            case TYPE_STRING:
                return String.valueOf(value);
            case TYPE_INT_ARRAY:
                return toIntArray(value);
            case TYPE_INTEGER_ARRAY:
                return toIntegerArray(toIntArray(value));
            case TYPE_BYTE_ARRAY:
                return value instanceof byte[] ? (byte[]) value : null;
            case TYPE_FLOAT_ARRAY:
                return value instanceof float[] ? (float[]) value : null;
            default:
                return null;
        }
    }

    public static int[] toIntArray(Object value) {
        if (value instanceof int[]) {
            return (int[]) value;
        }
        if (value instanceof Integer[]) {
            Integer[] boxed = (Integer[]) value;
            int[] result = new int[boxed.length];
            for (int i = 0; i < boxed.length; i++) {
                result[i] = boxed[i] == null ? 0 : boxed[i];
            }
            return result;
        }
        return null;
    }

    public static Integer[] toIntegerArray(int[] value) {
        if (value == null) {
            return null;
        }
        Integer[] result = new Integer[value.length];
        for (int i = 0; i < value.length; i++) {
            result[i] = value[i];
        }
        return result;
    }

    public static void writeValue(Parcel parcel, int type, Object value) {
        switch (type) {
            case TYPE_INT:
                parcel.writeInt(value instanceof Number ? ((Number) value).intValue() : 0);
                break;
            case TYPE_FLOAT:
                parcel.writeFloat(value instanceof Number ? ((Number) value).floatValue() : 0f);
                break;
            case TYPE_BOOL:
                parcel.writeInt(value instanceof Boolean && (Boolean) value ? 1 : 0);
                break;
            case TYPE_STRING:
                parcel.writeString(value == null ? null : String.valueOf(value));
                break;
            case TYPE_INT_ARRAY:
                parcel.writeIntArray(toIntArray(value));
                break;
            case TYPE_INTEGER_ARRAY:
                parcel.writeIntArray(toIntArray(value));
                break;
            case TYPE_BYTE_ARRAY:
                parcel.writeByteArray(value instanceof byte[] ? (byte[]) value : null);
                break;
            case TYPE_FLOAT_ARRAY:
                parcel.writeFloatArray(value instanceof float[] ? (float[]) value : null);
                break;
            default:
                break;
        }
    }

    public static Object readValue(Parcel parcel, int type) {
        switch (type) {
            case TYPE_INT:
                return parcel.readInt();
            case TYPE_FLOAT:
                return parcel.readFloat();
            case TYPE_BOOL:
                return parcel.readInt() != 0;
            case TYPE_STRING:
                return parcel.readString();
            case TYPE_INT_ARRAY:
                return parcel.createIntArray();
            case TYPE_INTEGER_ARRAY:
                return toIntegerArray(parcel.createIntArray());
            case TYPE_BYTE_ARRAY:
                return parcel.createByteArray();
            case TYPE_FLOAT_ARRAY:
                return parcel.createFloatArray();
            default:
                return null;
        }
    }

    public static void writeEvent(Parcel parcel, int propId, int areaId, int status,
            long timestamp, int type, Object value) {
        parcel.writeInt(propId);
        parcel.writeInt(areaId);
        parcel.writeInt(status);
        parcel.writeLong(timestamp);
        parcel.writeInt(type);
        writeValue(parcel, type, value);
    }
}
