package com.bandwa.vcarlib;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;

/** 模拟蓝牙栈：AVD 跳过 BluetoothService 致 adapter 为 null、launcher 蓝牙卡片闪退时，
 * 以 root 注册 bluetooth_manager（registerAdapter 回 null）兜底。 */
public final class BtShim {
    private static final String TAG = "BtShim";
    private static final String SERVICE_NAME = "bluetooth_manager";
    private static final String DESCRIPTOR = "android.bluetooth.IBluetoothManager";

    private BtShim() {
    }

    /** IBluetoothManager 最小应答：registerAdapter 返回 null，其余只回 no-exception。 */
    private static final class Stub extends Binder {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            try {
                if (code == 1) {
                    // 事务码 1：registerAdapter(callback) -> IBluetooth；回 null 即蓝牙未启用
                    data.enforceInterface(DESCRIPTOR);
                    data.readStrongBinder();
                    if (reply != null) {
                        reply.writeNoException();
                        reply.writeStrongBinder(null);
                    }
                    return true;
                }
                if (reply != null) {
                    reply.writeNoException();
                }
                return true;
            } catch (Throwable t) {
                Log.e(TAG, "onTransact(" + code + ") failed", t);
                return false;
            }
        }
    }

    // ServiceManager 是 @hide API，本程序按公开 android.jar 编译，故用反射。
    private static IBinder getExistingService() {
        try {
            Class<?> serviceManager = Class.forName("android.os.ServiceManager");
            return (IBinder) serviceManager.getMethod("getService", String.class)
                    .invoke(null, SERVICE_NAME);
        } catch (Throwable t) {
            Log.w(TAG, "getService failed: " + t);
            return null;
        }
    }

    private static void addService(IBinder binder) throws Exception {
        Class<?> serviceManager = Class.forName("android.os.ServiceManager");
        serviceManager.getMethod("addService", String.class, IBinder.class)
                .invoke(null, SERVICE_NAME, binder);
    }

    /** 由 avd/run.sh / run.ps1 在 boot 后以 adb root 启动（system_app 域被 SELinux 拒绝）。 */
    public static void main(String[] args) {
        try {
            if (getExistingService() != null) {
                Log.i(TAG, SERVICE_NAME + " already registered; keep alive");
            } else {
                addService(new Stub());
                Log.i(TAG, SERVICE_NAME + " registered (registerAdapter -> null)");
            }
        } catch (Throwable t) {
            Log.e(TAG, "register " + SERVICE_NAME + " failed", t);
        }
        // 必须常驻：进程退出会注销 service，launcher 下次启动又会 NPE。
        while (true) {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException ignored) {
            }
        }
    }
}
