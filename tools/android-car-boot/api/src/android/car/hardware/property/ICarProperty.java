package android.car.hardware.property;

import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

import java.util.List;

/** 对应 VendorServiceShims 属性垫片的 5 个事务码。 */
public interface ICarProperty extends android.os.IInterface {
    String DESCRIPTOR = "android.car.ICarProperty";

    void registerListener(int propId, float rate, ICarPropertyEventListener listener)
            throws RemoteException;

    void unregisterListener(int propId, ICarPropertyEventListener listener) throws RemoteException;

    List<CarPropertyConfig> getPropertyList() throws RemoteException;

    CarPropertyValue getProperty(int propId, int areaId) throws RemoteException;

    void setProperty(CarPropertyValue value) throws RemoteException;

    IBinder asBinder();

    abstract class Stub extends Binder implements ICarProperty {
        protected Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static ICarProperty asInterface(IBinder binder) {
            if (binder == null) {
                return null;
            }
            return new Proxy(binder);
        }

        @Override
        public IBinder asBinder() {
            return this;
        }
    }

    final class Proxy implements ICarProperty {
        private final IBinder remote;

        Proxy(IBinder remote) {
            this.remote = remote;
        }

        @Override
        public IBinder asBinder() {
            return remote;
        }

        @Override
        public void registerListener(int propId, float rate, ICarPropertyEventListener listener)
                throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeInt(propId);
                data.writeFloat(rate);
                data.writeStrongBinder(listener == null ? null : listener.asBinder());
                remote.transact(1, data, reply, 0);
                reply.readException();
            } finally {
                data.recycle();
                reply.recycle();
            }
        }

        @Override
        public void unregisterListener(int propId, ICarPropertyEventListener listener)
                throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeInt(propId);
                data.writeStrongBinder(listener == null ? null : listener.asBinder());
                remote.transact(2, data, reply, 0);
                reply.readException();
            } finally {
                data.recycle();
                reply.recycle();
            }
        }

        @Override
        public List<CarPropertyConfig> getPropertyList() throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                remote.transact(3, data, reply, 0);
                reply.readException();
                return reply.createTypedArrayList(CarPropertyConfig.CREATOR);
            } finally {
                data.recycle();
                reply.recycle();
            }
        }

        @Override
        public CarPropertyValue getProperty(int propId, int areaId) throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeInt(propId);
                data.writeInt(areaId);
                remote.transact(4, data, reply, 0);
                reply.readException();
                if (reply.readInt() == 0) {
                    return null;
                }
                return CarPropertyValue.CREATOR.createFromParcel(reply);
            } finally {
                data.recycle();
                reply.recycle();
            }
        }

        @Override
        public void setProperty(CarPropertyValue value) throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                if (value == null) {
                    data.writeInt(0);
                } else {
                    data.writeInt(1);
                    value.writeToParcel(data, 0);
                }
                remote.transact(5, data, reply, 0);
                reply.readException();
            } finally {
                data.recycle();
                reply.recycle();
            }
        }
    }
}
