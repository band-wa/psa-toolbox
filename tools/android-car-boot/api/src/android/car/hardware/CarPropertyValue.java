package android.car.hardware;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;

import com.bandwa.vcarlib.BootCarProtocol;

/**
 * AOSP {@code android.car.hardware.CarPropertyValue} 的 bootclasspath 替代实现。
 *
 * <p>注意：本类<b>没有</b> {@code implements Parcelable}。原因：ABI 基准
 * {@code third_party/android-car-stub.jar} 里的 CarPropertyValue 也不实现 Parcelable，
 * {@code check_abi.sh} 会比对 javap 类头（含 implements 列表），加上接口会让 ABI 检查失败。
 * 厂商车机 APK 只需要本类的 {@code CREATOR} 字段和 {@code writeToParcel(Parcel,int)} 方法
 * （其 bytecode 直接 invoke-virtual/invoke-interface，不要求类实现该接口），因此以
 * “公开成员超集”的方式提供：字段/方法存在即可。
 */
public class CarPropertyValue<T> {
    public static final int STATUS_AVAILABLE = 0;
    public static final int STATUS_UNAVAILABLE = 1;
    public static final int STATUS_ERROR = 2;

    private final int propertyId;
    private final int areaId;
    private final int status;
    private final long timestamp;
    private final T value;

    public CarPropertyValue(int propertyId, int areaId, T value) {
        this(propertyId, areaId, STATUS_AVAILABLE, SystemClock.uptimeMillis(), value);
    }

    public CarPropertyValue(int propertyId, int areaId, int status, long timestamp, T value) {
        this.propertyId = propertyId;
        this.areaId = areaId;
        this.status = status;
        this.timestamp = timestamp;
        this.value = value;
    }

    public int getPropertyId() {
        return propertyId;
    }

    public int getAreaId() {
        return areaId;
    }

    public int getStatus() {
        return status;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public T getValue() {
        return value;
    }

    /**
     * 与 AOSP Parcelable 版保持相同的方法名/描述符（厂商 bytecode 会直接调用），
     * 但不声明 implements Parcelable（见类注释）。
     */
    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(propertyId);
        dest.writeInt(areaId);
        dest.writeInt(status);
        dest.writeLong(timestamp);
        int type = BootCarProtocol.typeOf(value);
        dest.writeInt(type);
        BootCarProtocol.writeValue(dest, type, value);
    }

    /** {@link #CREATOR} 的载体；编码与本类 writeToParcel 一一对应。 */
    public static CarPropertyValue<?> readFromParcel(Parcel in) {
        int propertyId = in.readInt();
        int areaId = in.readInt();
        int status = in.readInt();
        long timestamp = in.readLong();
        int type = in.readInt();
        Object value = BootCarProtocol.readValue(in, type);
        return new CarPropertyValue<Object>(propertyId, areaId, status, timestamp, value);
    }

    /**
     * 厂商车机 APK 的 ICarProperty proxy 引用了
     * {@code Landroid/car/hardware/CarPropertyValue;->CREATOR:Landroid/os/Parcelable$Creator;}，
     * 类型必须精确为 {@link Parcelable.Creator}。
     */
    public static final Parcelable.Creator<CarPropertyValue> CREATOR =
            new Parcelable.Creator<CarPropertyValue>() {
                @Override
                public CarPropertyValue createFromParcel(Parcel in) {
                    return readFromParcel(in);
                }

                @Override
                public CarPropertyValue[] newArray(int size) {
                    return new CarPropertyValue[size];
                }
            };

    @Override
    public String toString() {
        return "CarPropertyValue{propertyId=0x" + Integer.toHexString(propertyId)
                + ", areaId=" + areaId + ", status=" + status + ", timestamp=" + timestamp
                + ", value=" + value + "}";
    }
}
