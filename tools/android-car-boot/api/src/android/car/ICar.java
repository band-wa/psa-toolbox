package android.car;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

/** AOSP 9 {@code ICar} 客户端垫片：code 2 取子服务，1/3 对齐服务端垫片。 */
public interface ICar extends android.os.IInterface {
    String DESCRIPTOR = "android.car.ICar";

    IBinder getCarService(String serviceName) throws RemoteException;

    void setCarServiceHelper(IBinder helper) throws RemoteException;

    int getCarConnectionType() throws RemoteException;

    IBinder asBinder();

    abstract class Stub extends Binder implements ICar {
        protected Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static ICar asInterface(IBinder binder) {
            return binder == null ? null : new Proxy(binder);
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
                data.readStrongBinder();
                return true;
            }
            if (code == 2) {
                data.enforceInterface(DESCRIPTOR);
                String name = data.readString();
                IBinder service = getCarService(name);
                if (reply != null) {
                    reply.writeNoException();
                    reply.writeStrongBinder(service);
                }
                return true;
            }
            if (code == 3) {
                data.enforceInterface(DESCRIPTOR);
                if (reply != null) {
                    reply.writeNoException();
                    reply.writeInt(5);
                }
                return true;
            }
            return true;
        }
    }

    final class Proxy implements ICar {
        private final IBinder remote;

        Proxy(IBinder remote) {
            this.remote = remote;
        }

        @Override
        public IBinder asBinder() {
            return remote;
        }

        @Override
        public IBinder getCarService(String serviceName) throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeString(serviceName);
                remote.transact(2, data, reply, 0);
                reply.readException();
                return reply.readStrongBinder();
            } finally {
                data.recycle();
                reply.recycle();
            }
        }

        @Override
        public void setCarServiceHelper(IBinder helper) throws RemoteException {
            Parcel data = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeStrongBinder(helper);
                remote.transact(1, data, null, 1);
            } finally {
                data.recycle();
            }
        }

        @Override
        public int getCarConnectionType() throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                remote.transact(3, data, reply, 0);
                reply.readException();
                return reply.readInt();
            } finally {
                data.recycle();
                reply.recycle();
            }
        }
    }
}
