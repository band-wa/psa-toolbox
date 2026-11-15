package android.car.hardware;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AOSP {@code android.car.hardware.CarPropertyConfig} 的 bootclasspath 替代实现。
 *
 * <p>与 {@link CarPropertyValue} 同理：<b>不</b>声明 {@code implements Parcelable}，否则
 * javap 类头会与 ABI 基准 {@code third_party/android-car-stub.jar} 不一致而让 check_abi.sh
 * 失败；厂商 APK 只需要 {@code CREATOR} 字段和 {@code writeToParcel(Parcel,int)} 方法。
 */
public class CarPropertyConfig<T> {
    public static final int CHANGE_MODE_STATIC = 0x01;
    public static final int CHANGE_MODE_ON_CHANGE = 0x02;
    public static final int CHANGE_MODE_CONTINUOUS = 0x04;

    /** AOSP 的访问权限位：1=可读，2=可写；厂商 car-lib 管理器用 getAccess() 过滤监听属性。 */
    public static final int ACCESS_READ = 0x01;
    public static final int ACCESS_WRITE = 0x02;

    private final int propertyId;
    private final int propertyType;
    private final int changeMode;
    private final boolean writable;
    private final List<Integer> areaIds;
    private final float minValue;
    private final float maxValue;

    /**
     * 公开无参构造：桩 jar 含此成员，保留以维持 ABI 超集。
     */
    public CarPropertyConfig() {
        this(0, 0, 0, false, null, 0f, 0f);
    }

    public CarPropertyConfig(int propertyId, int propertyType, int changeMode, boolean writable,
            int[] areaIds, float minValue, float maxValue) {
        this.propertyId = propertyId;
        this.propertyType = propertyType;
        this.changeMode = changeMode;
        this.writable = writable;
        List<Integer> ids = new ArrayList<>();
        if (areaIds != null) {
            for (int areaId : areaIds) {
                ids.add(areaId);
            }
        }
        this.areaIds = Collections.unmodifiableList(ids);
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    public int getPropertyId() {
        return propertyId;
    }

    public int getPropertyType() {
        return propertyType;
    }

    public int getChangeMode() {
        return changeMode;
    }

    public List<Integer> getAreaIds() {
        return areaIds;
    }

    public int getMinValue() {
        return (int) minValue;
    }

    public int getMaxValue() {
        return (int) maxValue;
    }

    public float getMinValue(float defaultValue) {
        return minValue;
    }

    public float getMaxValue(float defaultValue) {
        return maxValue;
    }

    public boolean isWritable() {
        return writable;
    }

    /**
     * AOSP 的访问权限位（1=可读、2=可写、3=可读写）。厂商 CarDYCabinManager 等按
     * {@code access == 1 || access == 3} 决定是否为该属性注册监听，这里统一返回 3。
     */
    public int getAccess() {
        return ACCESS_READ | ACCESS_WRITE;
    }

    /**
     * AOSP 的配置数组（例如传感器支持的采样率/类型枚举）。我们的自定义协议不下发这部分
     * 信息，返回空列表以避免厂商 car-lib（如 CarSensorManager）遍历时 NPE。
     */
    public List<Integer> getConfigArray() {
        return Collections.emptyList();
    }

    /** 与 AOSP Parcelable 版保持相同的方法名/描述符（厂商 bytecode 会直接调用）。 */
    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(propertyId);
        dest.writeInt(propertyType);
        dest.writeInt(changeMode);
        dest.writeInt(writable ? 1 : 0);
        dest.writeFloat(minValue);
        dest.writeFloat(maxValue);
        dest.writeInt(areaIds.size());
        for (Integer areaId : areaIds) {
            dest.writeInt(areaId);
        }
    }

    /** {@link #CREATOR} 的载体；编码与本类 writeToParcel 一一对应。 */
    public static CarPropertyConfig<?> readFromParcel(Parcel in) {
        int propertyId = in.readInt();
        int propertyType = in.readInt();
        int changeMode = in.readInt();
        boolean writable = in.readInt() != 0;
        float minValue = in.readFloat();
        float maxValue = in.readFloat();
        int areaCount = in.readInt();
        int[] areaIds = new int[areaCount];
        for (int i = 0; i < areaCount; i++) {
            areaIds[i] = in.readInt();
        }
        return new CarPropertyConfig<Object>(propertyId, propertyType, changeMode, writable,
                areaIds, minValue, maxValue);
    }

    /**
     * 厂商车机 APK 的 ICarProperty proxy 用
     * {@code createTypedArrayList(CarPropertyConfig.CREATOR)} 读取 getPropertyList() 结果。
     */
    public static final Parcelable.Creator<CarPropertyConfig> CREATOR =
            new Parcelable.Creator<CarPropertyConfig>() {
                @Override
                public CarPropertyConfig createFromParcel(Parcel in) {
                    return readFromParcel(in);
                }

                @Override
                public CarPropertyConfig[] newArray(int size) {
                    return new CarPropertyConfig[size];
                }
            };

    @Override
    public String toString() {
        return "CarPropertyConfig{id=0x" + Integer.toHexString(propertyId) + ", type="
                + propertyType + ", changeMode=" + changeMode + ", writable=" + writable + "}";
    }
}
