package com.android.car;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.bandwa.vcarlib.BootCarProtocol;

/**
 * 调试用广播接收器：不依赖服务是否已绑定，直接操作同进程单例 {@link CarState}。
 *
 * <p>action 为 {@value #ACTION_CONTROL}（com.android.car.CONTROL 广播）。
 * 命令：set / key / reset / sim / log。
 */
public class ControlReceiver extends BroadcastReceiver {
    /** 控制广播 action；Manifest 中 receiver 的 intent-filter 必须与此一致。 */
    public static final String ACTION_CONTROL = "com.android.car.CONTROL";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_CONTROL.equals(intent.getAction())) {
            return;
        }
        CarState state = CarState.get();
        String command = intent.getStringExtra("cmd");
        if (command == null) {
            command = "";
        }
        switch (command) {
            case "set":
                handleSet(state, intent);
                break;
            case "key":
                handleKey(state, intent);
                break;
            case "reset":
                state.reset();
                Log.i(CarState.TAG, "state reset");
                break;
            case "sim":
                state.setSimulation(parseInt(intent.getStringExtra("on"), 0) != 0);
                break;
            case "log":
                Log.i(CarState.TAG, state.snapshot());
                break;
            default:
                Log.w(CarState.TAG, "unknown command: " + command);
                break;
        }
    }

    private void handleSet(CarState state, Intent intent) {
        int propId = parseHex(intent.getStringExtra("prop"));
        String type = intent.getStringExtra("type");
        String raw = intent.getStringExtra("value");
        if (propId == 0) {
            Log.w(CarState.TAG, "set requires prop");
            return;
        }
        if (type == null) {
            type = state.typeOf(propId) == BootCarProtocol.TYPE_INT ? "int"
                    : state.typeOf(propId) == BootCarProtocol.TYPE_FLOAT ? "float"
                            : state.typeOf(propId) == BootCarProtocol.TYPE_BOOL ? "bool"
                                    : state.typeOf(propId) == BootCarProtocol.TYPE_STRING
                                            ? "string"
                                            : state.typeOf(propId)
                                                    == BootCarProtocol.TYPE_INT_ARRAY
                                                            ? "intarray" : "";
        }
        Object value = parseValue(type, raw);
        int result = state.forceWrite(propId, value);
        Log.i(CarState.TAG, "set 0x" + Integer.toHexString(propId) + " type=" + type + " raw="
                + raw + " -> " + result);
        setResultCode(result == BootCarProtocol.RESULT_OK ? Activity.RESULT_OK
                : Activity.RESULT_CANCELED);
    }

    private void handleKey(CarState state, Intent intent) {
        int keyId = parseHex(intent.getStringExtra("key"));
        int keyState = parseInt(intent.getStringExtra("state"), 1);
        if (keyId == 0) {
            keyId = CarState.PROP_MCU_KEY_POWER_LEFT;
        }
        state.pressMcuKey(keyId, keyState);
    }

    private static Object parseValue(String type, String raw) {
        if (raw == null) {
            return null;
        }
        switch (type == null ? "" : type) {
            case "int":
                return parseInt(raw, 0);
            case "float":
                try {
                    return Float.parseFloat(raw.trim());
                } catch (NumberFormatException e) {
                    return 0f;
                }
            case "bool":
                return "1".equals(raw) || "true".equalsIgnoreCase(raw);
            case "intarray": {
                String[] parts = raw.split(",");
                int[] values = new int[parts.length];
                for (int i = 0; i < parts.length; i++) {
                    values[i] = parseInt(parts[i], 0);
                }
                return values;
            }
            case "string":
            default:
                return raw;
        }
    }

    private static int parseHex(String value) {
        if (value == null) {
            return 0;
        }
        String trimmed = value.trim();
        try {
            if (trimmed.startsWith("0x") || trimmed.startsWith("0X")) {
                return (int) Long.parseLong(trimmed.substring(2), 16);
            }
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int parseInt(String value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return parseHex(value);
    }
}
