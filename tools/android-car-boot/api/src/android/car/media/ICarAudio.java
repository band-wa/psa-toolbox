package android.car.media;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

/** 事务码/应答对齐 VendorServiceShims 的 AUDIO 垫片：getter 带值、setter 只回成功，
 * createAudioPatch 回 int 0（null handle）。 */
public interface ICarAudio extends android.os.IInterface {
    String DESCRIPTOR = "android.car.media.ICarAudio";

    void setGroupVolume(int groupId, int index, int value) throws RemoteException;

    int getGroupMaxVolume(int groupId) throws RemoteException;

    int getGroupMinVolume(int groupId) throws RemoteException;

    int getGroupVolume(int groupId) throws RemoteException;

    void setFadeTowardFront(int value) throws RemoteException;

    int getFadeTowardFront() throws RemoteException;

    void setBalanceTowardRight(int value) throws RemoteException;

    int getBalanceTowardRight() throws RemoteException;

    void setBalFadBalance(int fade, int balance) throws RemoteException;

    CarAudioPatchHandle createAudioPatch(String deviceAddress, int usage, int gain)
            throws RemoteException;

    void releaseAudioPatch(CarAudioPatchHandle patch) throws RemoteException;

    int getVolumeGroupIdForUsage(int usage) throws RemoteException;

    void registerVolumeCallback(IBinder callback) throws RemoteException;

    void setMicMode(int mode) throws RemoteException;

    void setBeepLevel(int level) throws RemoteException;

    int getBeepLevel() throws RemoteException;

    void setMasterMute(boolean mute, int flags) throws RemoteException;

    void setEqMode(int mode) throws RemoteException;

    int getEqMode() throws RemoteException;

    void setAudioHighVoice(int value) throws RemoteException;

    int getAudioHighVoice() throws RemoteException;

    void setAudioMidVoice(int value) throws RemoteException;

    int getAudioMidVoice() throws RemoteException;

    void setAudioLowVoice(int value) throws RemoteException;

    int getAudioLowVoice() throws RemoteException;

    void switchMic(int mic) throws RemoteException;

    void setLoudNess(boolean enable) throws RemoteException;

    boolean getLoudNess() throws RemoteException;

    void setVolumeWithSpeed(boolean enable) throws RemoteException;

    boolean getVolumeWithSpeed() throws RemoteException;

    IBinder asBinder();

    abstract class Stub extends Binder implements ICarAudio {
        protected Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static ICarAudio asInterface(IBinder binder) {
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

    final class Proxy implements ICarAudio {
        private final IBinder remote;

        Proxy(IBinder remote) {
            this.remote = remote;
        }

        @Override
        public IBinder asBinder() {
            return remote;
        }

        private Parcel begin() {
            Parcel data = Parcel.obtain();
            data.writeInterfaceToken(DESCRIPTOR);
            return data;
        }

        private void callVoid(int code, Parcel data) throws RemoteException {
            Parcel reply = Parcel.obtain();
            try {
                remote.transact(code, data, reply, 0);
                reply.readException();
            } finally {
                data.recycle();
                reply.recycle();
            }
        }

        private int callInt(int code, Parcel data) throws RemoteException {
            Parcel reply = Parcel.obtain();
            try {
                remote.transact(code, data, reply, 0);
                reply.readException();
                return reply.readInt();
            } finally {
                data.recycle();
                reply.recycle();
            }
        }

        @Override
        public void setGroupVolume(int groupId, int index, int value) throws RemoteException {
            Parcel data = begin();
            data.writeInt(groupId);
            data.writeInt(index);
            data.writeInt(value);
            callVoid(0x01, data);
        }

        @Override
        public int getGroupMaxVolume(int groupId) throws RemoteException {
            Parcel data = begin();
            data.writeInt(groupId);
            return callInt(0x02, data);
        }

        @Override
        public int getGroupMinVolume(int groupId) throws RemoteException {
            Parcel data = begin();
            data.writeInt(groupId);
            return callInt(0x03, data);
        }

        @Override
        public int getGroupVolume(int groupId) throws RemoteException {
            Parcel data = begin();
            data.writeInt(groupId);
            return callInt(0x04, data);
        }

        @Override
        public void setFadeTowardFront(int value) throws RemoteException {
            Parcel data = begin();
            data.writeInt(value);
            callVoid(0x05, data);
        }

        @Override
        public int getFadeTowardFront() throws RemoteException {
            return callInt(0x06, begin());
        }

        @Override
        public void setBalanceTowardRight(int value) throws RemoteException {
            Parcel data = begin();
            data.writeInt(value);
            callVoid(0x07, data);
        }

        @Override
        public int getBalanceTowardRight() throws RemoteException {
            return callInt(0x08, begin());
        }

        @Override
        public void setBalFadBalance(int fade, int balance) throws RemoteException {
            Parcel data = begin();
            data.writeInt(fade);
            data.writeInt(balance);
            callVoid(0x09, data);
        }

        @Override
        public CarAudioPatchHandle createAudioPatch(String deviceAddress, int usage, int gain)
                throws RemoteException {
            Parcel data = begin();
            data.writeString(deviceAddress);
            data.writeInt(usage);
            data.writeInt(gain);
            Parcel reply = Parcel.obtain();
            try {
                remote.transact(0x0b, data, reply, 0);
                reply.readException();
                return reply.readInt() == 0 ? null : new CarAudioPatchHandle();
            } finally {
                data.recycle();
                reply.recycle();
            }
        }

        @Override
        public void releaseAudioPatch(CarAudioPatchHandle patch) throws RemoteException {
            callVoid(0x0c, begin());
        }

        @Override
        public int getVolumeGroupIdForUsage(int usage) throws RemoteException {
            Parcel data = begin();
            data.writeInt(usage);
            return callInt(0x0e, data);
        }

        @Override
        public void registerVolumeCallback(IBinder callback) throws RemoteException {
            Parcel data = begin();
            data.writeStrongBinder(callback);
            callVoid(0x10, data);
        }

        @Override
        public void setMicMode(int mode) throws RemoteException {
            Parcel data = begin();
            data.writeInt(mode);
            callVoid(0x12, data);
        }

        @Override
        public void setBeepLevel(int level) throws RemoteException {
            Parcel data = begin();
            data.writeInt(level);
            callVoid(0x13, data);
        }

        @Override
        public int getBeepLevel() throws RemoteException {
            return callInt(0x14, begin());
        }

        @Override
        public void setMasterMute(boolean mute, int flags) throws RemoteException {
            Parcel data = begin();
            data.writeInt(mute ? 1 : 0);
            data.writeInt(flags);
            callVoid(0x15, data);
        }

        @Override
        public void setEqMode(int mode) throws RemoteException {
            Parcel data = begin();
            data.writeInt(mode);
            callVoid(0x18, data);
        }

        @Override
        public int getEqMode() throws RemoteException {
            return callInt(0x19, begin());
        }

        @Override
        public void setAudioHighVoice(int value) throws RemoteException {
            Parcel data = begin();
            data.writeInt(value);
            callVoid(0x1a, data);
        }

        @Override
        public int getAudioHighVoice() throws RemoteException {
            return callInt(0x1b, begin());
        }

        @Override
        public void setAudioMidVoice(int value) throws RemoteException {
            Parcel data = begin();
            data.writeInt(value);
            callVoid(0x1c, data);
        }

        @Override
        public int getAudioMidVoice() throws RemoteException {
            return callInt(0x1d, begin());
        }

        @Override
        public void setAudioLowVoice(int value) throws RemoteException {
            Parcel data = begin();
            data.writeInt(value);
            callVoid(0x1e, data);
        }

        @Override
        public int getAudioLowVoice() throws RemoteException {
            return callInt(0x1f, begin());
        }

        @Override
        public void switchMic(int mic) throws RemoteException {
            Parcel data = begin();
            data.writeInt(mic);
            callVoid(0x21, data);
        }

        @Override
        public void setLoudNess(boolean enable) throws RemoteException {
            Parcel data = begin();
            data.writeInt(enable ? 1 : 0);
            callVoid(0x24, data);
        }

        @Override
        public boolean getLoudNess() throws RemoteException {
            return callInt(0x25, begin()) != 0;
        }

        @Override
        public void setVolumeWithSpeed(boolean enable) throws RemoteException {
            Parcel data = begin();
            data.writeInt(enable ? 1 : 0);
            callVoid(0x26, data);
        }

        @Override
        public boolean getVolumeWithSpeed() throws RemoteException {
            return callInt(0x27, begin()) != 0;
        }
    }
}
