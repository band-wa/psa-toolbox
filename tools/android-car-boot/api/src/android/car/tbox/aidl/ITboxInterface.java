package android.car.tbox.aidl;

import android.os.Binder;
import android.os.IBinder;

/**
 * 厂商 TBox AIDL 的 bootclasspath 实现：模拟环境没有 TBox 硬件，
 * asInterface 返回一个本地 no-op 实现（tboxIsExist=false）。
 */
public interface ITboxInterface extends android.os.IInterface {
    String DESCRIPTOR = "android.car.tbox.aidl.ITboxInterface";

    boolean tboxIsExist() throws android.os.RemoteException;

    void registerCallBack(ITboxCallBackInterface callback) throws android.os.RemoteException;

    void getWanInfo(INetworkSettingCallBackInterface callback) throws android.os.RemoteException;

    IBinder asBinder();

    abstract class Stub extends Binder implements ITboxInterface {
        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static ITboxInterface asInterface(IBinder binder) {
            return binder == null ? null : new Local();
        }

        @Override
        public IBinder asBinder() {
            return this;
        }

        @Override
        public boolean tboxIsExist() {
            return false;
        }

        @Override
        public void registerCallBack(ITboxCallBackInterface callback) {
        }

        @Override
        public void getWanInfo(INetworkSettingCallBackInterface callback) {
        }
    }

    /** 不经过 binder 的本地实现（launcher 只读状态，不涉及跨进程）。 */
    final class Local extends Stub {
    }
}
