package android.car.tbox.aidl;

import android.os.Binder;
import android.os.IBinder;

/** 厂商 TBox 回调 AIDL：launcher 会 new 匿名子类，这里保持具体类。 */
public interface ITboxCallBackInterface extends android.os.IInterface {
    String DESCRIPTOR = "android.car.tbox.aidl.ITboxCallBackInterface";

    IBinder asBinder();

    abstract class Stub extends Binder implements ITboxCallBackInterface {
        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        @Override
        public IBinder asBinder() {
            return this;
        }
    }
}
