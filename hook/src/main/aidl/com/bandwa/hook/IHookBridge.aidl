package com.bandwa.hook;

import com.bandwa.hook.IHookCallback;

interface IHookBridge {
    String getConfig(String key, String defValue);
    void reportEvent(String event, String payload);
    void registerCallback(IHookCallback callback);
    void unregisterCallback(IHookCallback callback);
    void registerCallbackEx(String packageName, String hookVersion, IHookCallback callback);
}
