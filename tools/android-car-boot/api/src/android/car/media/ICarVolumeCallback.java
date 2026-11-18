package android.car.media;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

/** 音量回调兼容桩：launcher 会 new 匿名子类，方法用空实现避免 AbstractMethodError。 */
public interface ICarVolumeCallback extends android.os.IInterface {
    String DESCRIPTOR = "android.car.media.ICarVolumeCallback";

    void onGroupVolumeChanged(int groupId, int flags) throws RemoteException;

    void onMasterMuteChanged(int flags) throws RemoteException;

    IBinder asBinder();

    abstract class Stub extends Binder implements ICarVolumeCallback {
        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static ICarVolumeCallback asInterface(IBinder binder) {
            if (binder == null) {
                return null;
            }
            return binder instanceof ICarVolumeCallback ? (ICarVolumeCallback) binder : new Proxy(binder);
        }

        @Override
        public IBinder asBinder() {
            return this;
        }

        @Override
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code == 1) {
                data.enforceInterface(DESCRIPTOR);
                onGroupVolumeChanged(data.readInt(), data.readInt());
                return true;
            }
            if (code == 2) {
                data.enforceInterface(DESCRIPTOR);
                onMasterMuteChanged(data.readInt());
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }

        @Override
        public void onGroupVolumeChanged(int groupId, int flags) {
        }

        @Override
        public void onMasterMuteChanged(int flags) {
        }
    }

    final class Proxy implements ICarVolumeCallback {
        private final IBinder remote;

        Proxy(IBinder remote) {
            this.remote = remote;
        }

        @Override
        public IBinder asBinder() {
            return remote;
        }

        @Override
        public void onGroupVolumeChanged(int groupId, int flags) throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeInt(groupId);
                data.writeInt(flags);
                remote.transact(1, data, reply, 0);
                reply.readException();
            } finally {
                data.recycle();
                reply.recycle();
            }
        }

        @Override
        public void onMasterMuteChanged(int flags) throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeInt(flags);
                remote.transact(2, data, reply, 0);
                reply.readException();
            } finally {
                data.recycle();
                reply.recycle();
            }
        }
    }
}
