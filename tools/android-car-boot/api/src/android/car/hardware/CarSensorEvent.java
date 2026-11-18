package android.car.hardware;

/** car-lib 只读 {@link EnvironmentData#temperature}，其余字段保留 AOSP 公开形状。 */
public class CarSensorEvent {
    public int sensorType;
    public long timestamp;
    public float[] floatValues;
    public int[] intValues;
    public long[] longValues;

    public static class EnvironmentData {
        public float temperature;
    }

    public EnvironmentData getEnvironmentData(EnvironmentData data) {
        if (data == null) {
            data = new EnvironmentData();
        }
        data.temperature = floatValues != null && floatValues.length > 0 ? floatValues[0] : 0f;
        return data;
    }
}
