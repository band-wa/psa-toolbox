package android.car.hardware.property;

import android.os.Binder;
import android.os.IBinder;

import java.util.List;

/** 厂商回调形状：事件按 List 投递；本环境不投递，只保证方法齐全。 */
public interface ICarPropertyEventListener extends android.os.IInterface {
    String DESCRIPTOR = "android.car.hardware.property.ICarPropertyEventListener";

    void onEvent(List<CarPropertyEvent> events);

    IBinder asBinder();

    /** 保持具体类（不声明抽象方法）以免匿名子类 AbstractMethodError。 */
    abstract class Stub extends Binder implements ICarPropertyEventListener {
        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        @Override
        public IBinder asBinder() {
            return this;
        }

        @Override
        public void onEvent(List<CarPropertyEvent> events) {
        }
    }
}
