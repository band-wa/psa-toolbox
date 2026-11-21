package android.car.media;

import android.car.CarManagerBase;
import android.content.Context;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

/**
 * AOSP {@code CarAudioManager} 的 bootclasspath 精简实现，属性/音量读写
 * 转给同一 binder 上的 ICarAudio（AUDIO 垫片，全部返回固定的假音量组）。
 */
public class CarAudioManager implements CarManagerBase {
    private static final String TAG = "CarAudioManager";

    private final ICarAudio carAudio;

    public CarAudioManager(IBinder binder, Context context) {
        this(binder, context, null);
    }

    public CarAudioManager(IBinder binder, Context context, Handler handler) {
        this.carAudio = ICarAudio.Stub.asInterface(binder);
    }

    public int getGroupMaxVolume(int groupId) {
        try {
            return carAudio.getGroupMaxVolume(groupId);
        } catch (Throwable t) {
            Log.w(TAG, "getGroupMaxVolume: " + t);
            return 0;
        }
    }

    public int getGroupMinVolume(int groupId) {
        try {
            return carAudio.getGroupMinVolume(groupId);
        } catch (Throwable t) {
            Log.w(TAG, "getGroupMinVolume: " + t);
            return 0;
        }
    }

    public int getGroupVolume(int groupId) {
        try {
            return carAudio.getGroupVolume(groupId);
        } catch (Throwable t) {
            Log.w(TAG, "getGroupVolume: " + t);
            return 0;
        }
    }

    public int getVolumeGroupIdForUsage(int usage) {
        try {
            return carAudio.getVolumeGroupIdForUsage(usage);
        } catch (Throwable t) {
            Log.w(TAG, "getVolumeGroupIdForUsage: " + t);
            return 0;
        }
    }

    public void setGroupVolume(int groupId, int index, int value) {
        try {
            carAudio.setGroupVolume(groupId, index, value);
        } catch (Throwable t) {
            Log.w(TAG, "setGroupVolume: " + t);
        }
    }

    public void setMasterMute(boolean mute, int flags) {
        try {
            carAudio.setMasterMute(mute, flags);
        } catch (Throwable t) {
            Log.w(TAG, "setMasterMute: " + t);
        }
    }

    public void registerVolumeCallback(IBinder callback) {
        try {
            carAudio.registerVolumeCallback(callback);
        } catch (Throwable t) {
            Log.w(TAG, "registerVolumeCallback: " + t);
        }
    }
}
