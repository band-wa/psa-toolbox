package com.bandwa.hook;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import java.io.File;
import java.lang.reflect.Method;
import java.util.concurrent.CopyOnWriteArrayList;

/** 注入宿主应用的入口；未连接工具箱时全部回退原厂行为。 */
public final class Hook {
    public static final String TAG = "BandwaHook";

    /**
     * 注入层的版本。改注入内容（比如加按键映射的 psaHandle 载荷）就要 +1：
     * 已经打过补丁的车机会因为上报的版本与这里不一致而重新打补丁。
     */
    public static final String VERSION = "5";
    public static final String LAUNCHER_VERSION = "5";

    private static final String VENDOR_BCALL_PROP = "tbox.bcall.running";
    private static final String DEFAULT_WALLPAPER_PATH = "/storage/emulated/0/launcherPic/wallpaper.png";
    private static final int MAX_PENDING_ACTIONS = 16;
    private static final long PENDING_TIMEOUT_MS = 1500;
    private static final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private static final CopyOnWriteArrayList<Runnable> pendingActions = new CopyOnWriteArrayList<>();
    public static final String TOOLBOX_PACKAGE = "com.bandwa.openadb";
    public static final String TOOLBOX_SERVICE = "com.bandwa.openadb.service.HookBridgeService";

    public interface Listener {
        void onEvent(String event, String payload);
    }

    private static final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private static volatile boolean installed;
    private static volatile Context appContext;
    private static volatile IHookBridge bridge;
    private static volatile boolean hvacForeground;

    private static final IHookCallback callback = new IHookCallback.Stub() {
        @Override
        public void onEvent(String event, String payload) {
            if ("hvac_foreground".equals(event)) {
                hvacForeground = "true".equals(payload);
            }
            for (Listener listener : listeners) {
                try {
                    listener.onEvent(event, payload);
                } catch (Throwable ignored) {
                }
            }
        }
    };

    private Hook() {
    }

    public static void install(Context context) {
        if (context == null) {
            return;
        }
        try {
            Context app = context.getApplicationContext();
            appContext = app != null ? app : context;
            if (installed) {
                return;
            }
            installed = true;
            Log.i(TAG, "installed v" + ownVersion() + " in " + appContext.getPackageName());
            bindBridge(appContext);
        } catch (Throwable t) {
            Log.e(TAG, "install failed", t);
        }
    }

    public static void addListener(Listener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public static void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public static String getConfig(String key, String defValue) {
        IHookBridge b = bridge;
        if (b == null) {
            return defValue;
        }
        try {
            return b.getConfig(key, defValue);
        } catch (Throwable t) {
            return defValue;
        }
    }

    public static boolean isHvacForeground() {
        return hvacForeground;
    }

    public static boolean isBcallRunning() {
        return getPropBool(VENDOR_BCALL_PROP) || isHvacForeground();
    }

    private static boolean getPropBool(String key) {
        return "true".equals(getProp(key, "false"));
    }

    public static void reportEvent(String event, String payload) {
        IHookBridge b = bridge;
        if (b == null) {
            return;
        }
        try {
            b.reportEvent(event, payload);
        } catch (Throwable ignored) {
        }
    }

    public static String getProp(String key, String defValue) {
        try {
            Class<?> cls = Class.forName("android.os.SystemProperties");
            Method get = cls.getMethod("get", String.class, String.class);
            Object value = get.invoke(null, key, defValue);
            return value instanceof String ? (String) value : defValue;
        } catch (Throwable t) {
            return defValue;
        }
    }

    public static void applyCardAlpha(View view) {
        if (view == null) {
            return;
        }
        final java.lang.ref.WeakReference<View> ref = new java.lang.ref.WeakReference<>(view);
        runWhenConnected(() -> {
            View target = ref.get();
            if (target == null) {
                return;
            }
            try {
                float alpha = Float.parseFloat(getConfig("card_alpha", "0.75"));
                target.setAlpha(alpha);
            } catch (Throwable t) {
                Log.w(TAG, "applyCardAlpha failed: " + t);
            }
        });
    }

    public static void applyWallpaper(View view) {
        if (view == null) {
            return;
        }
        final java.lang.ref.WeakReference<View> ref = new java.lang.ref.WeakReference<>(view);
        runWhenConnected(() -> {
            View target = ref.get();
            if (target == null) {
                return;
            }
            try {
                String path = getConfig("wallpaper_path", DEFAULT_WALLPAPER_PATH);
                if (path.length() == 0 || !new File(path).exists()) {
                    Log.i(TAG, "wallpaper missing: " + path);
                    return;
                }
                Drawable drawable = Drawable.createFromPath(path);
                if (drawable != null) {
                    target.setBackground(drawable);
                    Log.i(TAG, "wallpaper applied: " + path + " on " + target.getClass().getName());
                } else {
                    Log.w(TAG, "wallpaper decode failed: " + path);
                }
            } catch (Throwable t) {
                Log.w(TAG, "applyWallpaper failed: " + t);
            }
        });
    }

    private static void runWhenConnected(Runnable action) {
        if (bridge != null) {
            action.run();
            return;
        }
        if (pendingActions.size() >= MAX_PENDING_ACTIONS) {
            return;
        }
        pendingActions.add(action);
        mainHandler.postDelayed(Hook::runPendingActions, PENDING_TIMEOUT_MS);
    }

    private static void runPendingActions() {
        for (Runnable action : pendingActions) {
            try {
                action.run();
            } catch (Throwable t) {
                Log.w(TAG, "pending action failed: " + t);
            }
        }
    }

    public static void launchByProp(String propKey, String defPackage) {
        launchByProp(currentApplication(), propKey, defPackage, null);
    }

    public static void launchByProp(String propKey, String defPackage, String defClass) {
        launchByProp(currentApplication(), propKey, defPackage, defClass);
    }

    public static void launchByProp(Context context, String propKey, String defPackage, String defClass) {
        try {
            if (context == null) return;
            if (bridge == null) {
                launchComponent(context, defPackage, defClass);
                return;
            }
            String pkg = getProp(propKey, "");
            if (pkg == null || pkg.length() == 0) {
                launchComponent(context, defPackage, defClass);
                return;
            }
            launchPackage(context, pkg);
        } catch (Throwable t) {
            Log.e(TAG, "launchByProp failed: " + propKey, t);
        }
    }

    public static void launchComponent(Context context, String pkg, String cls) {
        if (pkg == null || pkg.length() == 0) return;
        if (cls == null || cls.length() == 0) {
            launchPackage(context, pkg);
            return;
        }
        try {
            Intent intent = new Intent();
            intent.setClassName(pkg, cls);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            Log.i(TAG, "launch " + pkg + "/" + cls);
        } catch (Throwable t) {
            Log.e(TAG, "launch failed: " + pkg + "/" + cls, t);
        }
    }

    public static void launchPackage(Context context, String pkg) {
        try {
            if (context == null || pkg == null || pkg.length() == 0) {
                return;
            }
            PackageManager pm = context.getPackageManager();
            Intent intent = pm.getLaunchIntentForPackage(pkg);
            if (intent == null) {
                Log.w(TAG, "no launch intent: " + pkg);
                return;
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            Log.i(TAG, "launch " + pkg);
        } catch (Throwable t) {
            Log.e(TAG, "launch failed: " + pkg, t);
        }
    }

    private static Context currentApplication() {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object application = activityThread.getMethod("currentApplication").invoke(null);
            return application instanceof Context ? (Context) application : null;
        } catch (Throwable t) {
            Log.w(TAG, "currentApplication failed", t);
            return null;
        }
    }

    private static String ownVersion() {
        String pkg = appContext != null ? appContext.getPackageName() : null;
        return "com.android.launcher".equals(pkg) ? LAUNCHER_VERSION : VERSION;
    }

    private static void bindBridge(Context context) {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(TOOLBOX_PACKAGE, TOOLBOX_SERVICE));
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        } catch (Throwable t) {
            Log.w(TAG, "bind bridge failed", t);
        }
    }

    private static final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                IHookBridge b = IHookBridge.Stub.asInterface(service);
                bridge = b;
                String pkg = appContext != null ? appContext.getPackageName() : "?";
                b.registerCallbackEx(pkg, ownVersion(), callback);
                Log.i(TAG, "bridge connected");
                hvacForeground = "true".equals(b.getConfig("hvac_foreground", "false"));
                runPendingActions();
                pendingActions.clear();
                reportEvent("hook_ready", ownVersion() + "@" + pkg);
            } catch (Throwable t) {
                Log.w(TAG, "bridge connect failed", t);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bridge = null;
            hvacForeground = false;
            Log.w(TAG, "bridge disconnected");
            rebind(0);
        }
    };

    private static boolean toolboxInstalled() {
        try {
            return appContext != null
                    && appContext.getPackageManager().getPackageInfo(TOOLBOX_PACKAGE, 0) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void rebind(int attempt) {
        if (bridge != null) return;
        long delayMs = attempt < 10 ? 2000L : 10000L;
        if (attempt > 40) return;
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (bridge != null || appContext == null || !toolboxInstalled()) return;
                bindBridge(appContext);
                rebind(attempt + 1);
            }
        }, delayMs);
    }
}
