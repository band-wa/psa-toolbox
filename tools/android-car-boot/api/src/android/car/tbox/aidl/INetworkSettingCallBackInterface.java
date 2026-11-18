package android.car.tbox.aidl;

import android.os.Binder;
import android.os.IBinder;

/** TBox 网络信息回调：launcher 会 new 匿名子类，这里保持具体类。 */
public interface INetworkSettingCallBackInterface extends android.os.IInterface {
    String DESCRIPTOR = "android.car.tbox.aidl.INetworkSettingCallBackInterface";

    IBinder asBinder();

    abstract class Stub extends Binder implements INetworkSettingCallBackInterface {
        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        @Override
        public IBinder asBinder() {
            return this;
        }
    }
}
