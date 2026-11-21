package android.car.media;

/** AOSP {@code CarAudioPatchHandle} 的精简实现（模拟环境不建真实音频通道）。 */
public class CarAudioPatchHandle {
    private final int handle;

    public CarAudioPatchHandle() {
        this(0);
    }

    public CarAudioPatchHandle(int handle) {
        this.handle = handle;
    }

    public int getHandle() {
        return handle;
    }

    @Override
    public String toString() {
        return "CarAudioPatchHandle(0x" + Integer.toHexString(handle) + ")";
    }
}
