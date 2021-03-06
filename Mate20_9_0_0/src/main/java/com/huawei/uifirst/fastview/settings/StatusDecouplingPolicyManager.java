package com.huawei.uifirst.fastview.settings;

import android.os.SystemProperties;
import com.huawei.uifirst.fastview.settings.StatusDecouplingPolicy.CallBack;

public class StatusDecouplingPolicyManager {
    private static final boolean MODEL_SETTINGS_ALL_PROP = SystemProperties.getBoolean("settings_fastview_fast_response_enable", false);
    public static final int MODEL_SETTINGS_BLUETOOTH = 12;
    private static final boolean MODEL_SETTINGS_BLUETOOTH_PROP = true;
    public static final int MODEL_SETTINGS_HOTSPOT = 13;
    private static final boolean MODEL_SETTINGS_HOTSPOT_PROP = true;
    public static final int MODEL_SETTINGS_INSTANTSHARE = 14;
    private static final boolean MODEL_SETTINGS_INSTANTSHARE_PROP = true;
    public static final int MODEL_SETTINGS_NFC = 15;
    private static final boolean MODEL_SETTINGS_NFC_PROP = true;
    public static final int MODEL_SETTINGS_WLAN = 11;
    private static final boolean MODEL_SETTINGS_WLAN_PROP = true;
    private IStatusDecoupling mStatusDecoupling;

    public IStatusDecoupling addStatusDecoupling(int modelID, CallBack sdpCallback, int delayTime) {
        if (isStatusDecouplingPolicyAvailable(modelID)) {
            this.mStatusDecoupling = new StatusDecouplingPolicy(sdpCallback, delayTime, getModelName(modelID));
        } else {
            this.mStatusDecoupling = new StatusDecouplingDummy();
        }
        return this.mStatusDecoupling;
    }

    public boolean isStatusDecouplingPolicyAvailable(int modelID) {
        switch (modelID) {
            case 11:
                return MODEL_SETTINGS_ALL_PROP;
            case 12:
                return MODEL_SETTINGS_ALL_PROP;
            case 13:
                return MODEL_SETTINGS_ALL_PROP;
            case 14:
                return MODEL_SETTINGS_ALL_PROP;
            case 15:
                return MODEL_SETTINGS_ALL_PROP;
            default:
                return false;
        }
    }

    public String getModelName(int modelID) {
        String modelName = "";
        switch (modelID) {
            case 11:
                return "Settings_Wifi";
            case 12:
                return "Settings_Bluetooth";
            case 13:
                return "Settings_Hotspot";
            case 14:
                return "Settings_Instantshare";
            case 15:
                return "Settings_Nfc";
            default:
                return "None";
        }
    }
}
