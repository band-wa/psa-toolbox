package android.car;

import android.car.CarManagerBase;
import android.content.Context;
import android.os.Handler;
import android.os.IBinder;

/** {@code CarInfoManager} 占位实现，保持 Car 的 info 服务名映射可用。 */
public class CarInfoManager implements CarManagerBase {
    public CarInfoManager(IBinder binder, Context context) {
    }

    public CarInfoManager(IBinder binder, Context context, Handler handler) {
    }
}
