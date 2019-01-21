package com.huawei.android.hardware.fmradio;

import android.util.Log;
import java.nio.charset.Charset;

public class FmReceiver extends FmTransceiver {
    public static final int FM_RX_AUDIO_MODE_MONO = 1;
    public static final int FM_RX_AUDIO_MODE_STEREO = 0;
    public static final int FM_RX_DWELL_PERIOD_1S = 1;
    public static final int FM_RX_DWELL_PERIOD_2S = 2;
    public static final int FM_RX_DWELL_PERIOD_3S = 3;
    public static final int FM_RX_DWELL_PERIOD_4S = 4;
    public static final int FM_RX_DWELL_PERIOD_5S = 5;
    public static final int FM_RX_DWELL_PERIOD_6S = 6;
    public static final int FM_RX_DWELL_PERIOD_7S = 7;
    public static final int FM_RX_LOW_POWER_MODE = 1;
    public static final int FM_RX_MUTE = 1;
    public static final int FM_RX_NORMAL_POWER_MODE = 0;
    public static final int FM_RX_RDS_GRP_AF_EBL = 4;
    public static final int FM_RX_RDS_GRP_PS_EBL = 2;
    public static final int FM_RX_RDS_GRP_PS_SIMPLE_EBL = 16;
    public static final int FM_RX_RDS_GRP_RT_EBL = 1;
    private static final int FM_RX_RSSI_LEVEL_STRONG = -96;
    private static final int FM_RX_RSSI_LEVEL_VERY_STRONG = -90;
    private static final int FM_RX_RSSI_LEVEL_VERY_WEAK = -105;
    private static final int FM_RX_RSSI_LEVEL_WEAK = -100;
    public static final int FM_RX_SCREEN_OFF_MODE = 0;
    public static final int FM_RX_SCREEN_ON_MODE = 1;
    public static final int FM_RX_SEARCHDIR_DOWN = 0;
    public static final int FM_RX_SEARCHDIR_UP = 1;
    public static final int FM_RX_SIGNAL_STRENGTH_STRONG = 2;
    public static final int FM_RX_SIGNAL_STRENGTH_VERY_STRONG = 3;
    public static final int FM_RX_SIGNAL_STRENGTH_VERY_WEAK = 0;
    public static final int FM_RX_SIGNAL_STRENGTH_WEAK = 1;
    public static final int FM_RX_SRCHLIST_MAX_STATIONS = 12;
    public static final int FM_RX_SRCHLIST_MODE_STRONG = 2;
    public static final int FM_RX_SRCHLIST_MODE_STRONGEST = 8;
    public static final int FM_RX_SRCHLIST_MODE_WEAK = 3;
    public static final int FM_RX_SRCHLIST_MODE_WEAKEST = 9;
    public static final int FM_RX_SRCHRDS_MODE_SCAN_PTY = 5;
    public static final int FM_RX_SRCHRDS_MODE_SEEK_AF = 7;
    public static final int FM_RX_SRCHRDS_MODE_SEEK_PI = 6;
    public static final int FM_RX_SRCHRDS_MODE_SEEK_PTY = 4;
    public static final int FM_RX_SRCH_MODE_SCAN = 1;
    public static final int FM_RX_SRCH_MODE_SEEK = 0;
    public static final int FM_RX_UNMUTE = 0;
    static final int STD_BUF_SIZE = 128;
    private static final String TAG = "FMRadio";
    private static final int TAVARUA_BUF_AF_LIST = 5;
    private static final int TAVARUA_BUF_EVENTS = 1;
    private static final int TAVARUA_BUF_MAX = 6;
    private static final int TAVARUA_BUF_PS_RDS = 3;
    private static final int TAVARUA_BUF_RAW_RDS = 4;
    private static final int TAVARUA_BUF_RT_RDS = 2;
    private static final int TAVARUA_BUF_SRCH_LIST = 0;
    private static final int V4L2_CID_PRIVATE_BASE = 134217728;
    private static final int V4L2_CID_PRIVATE_TAVARUA_ANTENNA = 134217746;
    private static final int V4L2_CID_PRIVATE_TAVARUA_SIGNAL_TH = 134217736;
    public static int mSearchState = 0;
    private FmRxEvCallbacksAdaptor mCallback;

    public FmReceiver() {
        this.mControl = new FmRxControls();
        this.mRdsData = new FmRxRdsData(sFd);
        this.mRxEvents = new FmRxEventListner();
    }

    public FmReceiver(String devicePath, FmRxEvCallbacksAdaptor callback) throws InstantiationException {
        this.mControl = new FmRxControls();
        this.mRxEvents = new FmRxEventListner();
        this.mCallback = callback;
    }

    public boolean registerClient(FmRxEvCallbacks callback) {
        return super.registerClient(callback);
    }

    public boolean unregisterClient() {
        return super.unregisterClient();
    }

    public boolean enable(FmConfig configSettings) {
        if (!super.enable(configSettings, 1)) {
            return false;
        }
        boolean status = registerClient(this.mCallback);
        this.mRdsData = new FmRxRdsData(sFd);
        return status;
    }

    public boolean reset() {
        if (getFMState() == 0) {
            Log.d(TAG, "FM already turned Off.");
            return false;
        }
        FmTransceiver.setFMPowerState(0);
        Log.v(TAG, "reset: NEW-STATE : FMState_Turned_Off");
        boolean status = unregisterClient();
        release("/dev/radio0");
        return status;
    }

    public boolean disable() {
        unregisterClient();
        super.disable();
        return true;
    }

    public boolean searchStations(int mode, int dwellPeriod, int direction) {
        String str;
        StringBuilder stringBuilder;
        boolean bStatus = true;
        Log.d(TAG, "Basic search...");
        if (!(mode == 0 || mode == 1)) {
            str = TAG;
            stringBuilder = new StringBuilder();
            stringBuilder.append("Invalid search mode: ");
            stringBuilder.append(mode);
            Log.d(str, stringBuilder.toString());
            bStatus = false;
        }
        if (dwellPeriod < 1 || dwellPeriod > 7) {
            str = TAG;
            stringBuilder = new StringBuilder();
            stringBuilder.append("Invalid dwelling time: ");
            stringBuilder.append(dwellPeriod);
            Log.d(str, stringBuilder.toString());
            bStatus = false;
        }
        if (!(direction == 0 || direction == 1)) {
            str = TAG;
            stringBuilder = new StringBuilder();
            stringBuilder.append("Invalid search direction: ");
            stringBuilder.append(direction);
            Log.d(str, stringBuilder.toString());
            bStatus = false;
        }
        if (bStatus) {
            str = TAG;
            stringBuilder = new StringBuilder();
            stringBuilder.append("searchStations: mode ");
            stringBuilder.append(mode);
            stringBuilder.append("direction:  ");
            stringBuilder.append(direction);
            Log.d(str, stringBuilder.toString());
            this.mControl.searchStations(sFd, mode, dwellPeriod, direction, 0, 0);
        }
        return true;
    }

    public boolean searchStations(int mode, int dwellPeriod, int direction, int pty, int pi) {
        String str;
        StringBuilder stringBuilder;
        int i = mode;
        int i2 = dwellPeriod;
        int i3 = direction;
        boolean bStatus = true;
        Log.d(TAG, "RDS search...");
        if (!(i == 4 || i == 5 || i == 6 || i == 7)) {
            String str2 = TAG;
            StringBuilder stringBuilder2 = new StringBuilder();
            stringBuilder2.append("Invalid search mode: ");
            stringBuilder2.append(i);
            Log.d(str2, stringBuilder2.toString());
            bStatus = false;
        }
        if (i2 < 1 || i2 > 7) {
            str = TAG;
            stringBuilder = new StringBuilder();
            stringBuilder.append("Invalid dwelling time: ");
            stringBuilder.append(i2);
            Log.d(str, stringBuilder.toString());
            bStatus = false;
        }
        if (!(i3 == 0 || i3 == 1)) {
            str = TAG;
            stringBuilder = new StringBuilder();
            stringBuilder.append("Invalid search direction: ");
            stringBuilder.append(i3);
            Log.d(str, stringBuilder.toString());
            bStatus = false;
        }
        int i4;
        int i5;
        if (bStatus) {
            String str3 = TAG;
            StringBuilder stringBuilder3 = new StringBuilder();
            stringBuilder3.append("searchStations: mode ");
            stringBuilder3.append(i);
            Log.d(str3, stringBuilder3.toString());
            str3 = TAG;
            stringBuilder3 = new StringBuilder();
            stringBuilder3.append("searchStations: dwellPeriod ");
            stringBuilder3.append(i2);
            Log.d(str3, stringBuilder3.toString());
            str3 = TAG;
            stringBuilder3 = new StringBuilder();
            stringBuilder3.append("searchStations: direction ");
            stringBuilder3.append(i3);
            Log.d(str3, stringBuilder3.toString());
            str3 = TAG;
            stringBuilder3 = new StringBuilder();
            stringBuilder3.append("searchStations: pty ");
            i4 = pty;
            stringBuilder3.append(i4);
            Log.d(str3, stringBuilder3.toString());
            str3 = TAG;
            stringBuilder3 = new StringBuilder();
            stringBuilder3.append("searchStations: pi ");
            i5 = pi;
            stringBuilder3.append(i5);
            Log.d(str3, stringBuilder3.toString());
            this.mControl.searchStations(sFd, i, i2, i3, i4, i5);
        } else {
            i4 = pty;
            i5 = pi;
        }
        return true;
    }

    public boolean searchStationList(int mode, int direction, int maximumStations, int pty) {
        boolean bStatus = true;
        int re = 0;
        String str = TAG;
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("searchStations: mode ");
        stringBuilder.append(mode);
        Log.d(str, stringBuilder.toString());
        str = TAG;
        stringBuilder = new StringBuilder();
        stringBuilder.append("searchStations: direction ");
        stringBuilder.append(direction);
        Log.d(str, stringBuilder.toString());
        str = TAG;
        stringBuilder = new StringBuilder();
        stringBuilder.append("searchStations: maximumStations ");
        stringBuilder.append(maximumStations);
        Log.d(str, stringBuilder.toString());
        str = TAG;
        stringBuilder = new StringBuilder();
        stringBuilder.append("searchStations: pty ");
        stringBuilder.append(pty);
        Log.d(str, stringBuilder.toString());
        if (!(mode == 2 || mode == 3 || mode == 8 || mode == 9)) {
            bStatus = false;
        }
        if (maximumStations < 0 || maximumStations > 12) {
            bStatus = false;
        }
        if (!(direction == 0 || direction == 1)) {
            bStatus = false;
        }
        if (bStatus) {
            if (mode == 8 || mode == 9) {
                re = this.mControl.searchStationList(sFd, mode, 0, direction, pty);
            } else {
                re = this.mControl.searchStationList(sFd, mode, maximumStations, direction, pty);
            }
        }
        if (re == 0) {
            return true;
        }
        return false;
    }

    public boolean cancelSearch() {
        this.mControl.cancelSearch(sFd);
        return true;
    }

    public boolean setMuteMode(int mode) {
        switch (mode) {
            case 0:
                this.mControl.muteControl(sFd, false);
                break;
            case 1:
                this.mControl.muteControl(sFd, true);
                break;
        }
        return true;
    }

    public boolean setStereoMode(boolean stereoEnable) {
        if (this.mControl.stereoControl(sFd, stereoEnable) == 0) {
            return true;
        }
        return false;
    }

    public boolean setSignalThreshold(int threshold) {
        int rssiLev;
        boolean bStatus = true;
        String str = TAG;
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Signal Threshhold input: ");
        stringBuilder.append(threshold);
        Log.d(str, stringBuilder.toString());
        switch (threshold) {
            case 0:
                rssiLev = FM_RX_RSSI_LEVEL_VERY_WEAK;
                break;
            case 1:
                rssiLev = FM_RX_RSSI_LEVEL_WEAK;
                break;
            case 2:
                rssiLev = FM_RX_RSSI_LEVEL_STRONG;
                break;
            case 3:
                rssiLev = FM_RX_RSSI_LEVEL_VERY_STRONG;
                break;
            default:
                String str2 = TAG;
                StringBuilder stringBuilder2 = new StringBuilder();
                stringBuilder2.append("Invalid threshold: ");
                stringBuilder2.append(threshold);
                Log.d(str2, stringBuilder2.toString());
                return false;
        }
        if (FmReceiverWrapper.setControlNative(sFd, V4L2_CID_PRIVATE_TAVARUA_SIGNAL_TH, rssiLev) != 0) {
            bStatus = false;
        }
        return bStatus;
    }

    public int getTunedFrequency() {
        int frequency = FmReceiverWrapper.getFreqNative(sFd);
        String str = TAG;
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("getFrequency: ");
        stringBuilder.append(frequency);
        Log.d(str, stringBuilder.toString());
        return frequency;
    }

    public FmRxRdsData getPSInfo() {
        byte[] buff = new byte[128];
        FmReceiverWrapper.getBufferNative(sFd, buff, 3);
        this.mRdsData.setPrgmId(((buff[2] & 255) << 8) | (buff[3] & 255));
        this.mRdsData.setPrgmType(buff[1] & 31);
        int numOfPs = buff[0] & 15;
        try {
            this.mRdsData.setPrgmServices(new String(buff, 5, numOfPs * 8, Charset.forName("UTF-8")));
        } catch (StringIndexOutOfBoundsException e) {
            String str = TAG;
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("Number of PS names ");
            stringBuilder.append(numOfPs);
            Log.d(str, stringBuilder.toString());
        }
        return this.mRdsData;
    }

    public FmRxRdsData getRTInfo() {
        byte[] buff = new byte[128];
        FmReceiverWrapper.getBufferNative(sFd, buff, 2);
        String rdsStr = new String(buff, Charset.forName("UTF-8"));
        this.mRdsData.setPrgmId(((buff[2] & 255) << 8) | (buff[3] & 255));
        this.mRdsData.setPrgmType(buff[1] & 31);
        try {
            this.mRdsData.setRadioText(rdsStr.substring(5, buff[0] + 5));
        } catch (StringIndexOutOfBoundsException e) {
            Log.d(TAG, "StringIndexOutOfBoundsException ...");
        }
        return this.mRdsData;
    }

    public int[] getAFInfo() {
        byte[] buff = new byte[128];
        int[] AfList = new int[40];
        FmReceiverWrapper.getBufferNative(sFd, buff, 5);
        if (buff[4] <= (byte) 0 || buff[4] > (byte) 25) {
            return null;
        }
        int lowerBand = FmReceiverWrapper.getLowerBandNative(sFd);
        String str = TAG;
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Low band ");
        stringBuilder.append(lowerBand);
        Log.d(str, stringBuilder.toString());
        str = TAG;
        stringBuilder = new StringBuilder();
        stringBuilder.append("AF_buff 0: ");
        byte i = (byte) 0;
        stringBuilder.append(buff[0] & 255);
        Log.d(str, stringBuilder.toString());
        str = TAG;
        stringBuilder = new StringBuilder();
        stringBuilder.append("AF_buff 1: ");
        stringBuilder.append(buff[1] & 255);
        Log.d(str, stringBuilder.toString());
        str = TAG;
        stringBuilder = new StringBuilder();
        stringBuilder.append("AF_buff 2: ");
        stringBuilder.append(buff[2] & 255);
        Log.d(str, stringBuilder.toString());
        str = TAG;
        stringBuilder = new StringBuilder();
        stringBuilder.append("AF_buff 3: ");
        stringBuilder.append(buff[3] & 255);
        Log.d(str, stringBuilder.toString());
        str = TAG;
        stringBuilder = new StringBuilder();
        stringBuilder.append("AF_buff 4: ");
        stringBuilder.append(buff[4] & 255);
        Log.d(str, stringBuilder.toString());
        while (true) {
            byte i2 = i;
            if (i2 >= buff[4]) {
                return AfList;
            }
            AfList[i2] = ((buff[i2 + 4] & 255) * 1000) + lowerBand;
            String str2 = TAG;
            StringBuilder stringBuilder2 = new StringBuilder();
            stringBuilder2.append("AF : ");
            stringBuilder2.append(AfList[i2]);
            Log.d(str2, stringBuilder2.toString());
            i = i2 + 1;
        }
    }

    public boolean setPowerMode(int powerMode) {
        int re;
        if (powerMode == 1) {
            re = this.mControl.setLowPwrMode(sFd, true);
        } else {
            re = this.mControl.setLowPwrMode(sFd, false);
        }
        return re == 0;
    }

    public int getPowerMode() {
        return this.mControl.getPwrMode(sFd);
    }

    public int[] getRssiLimit() {
        return new int[]{0, 100};
    }

    public int getSignalThreshold() {
        int signalStrength;
        int rmssiThreshold = FmReceiverWrapper.getControlNative(sFd, V4L2_CID_PRIVATE_TAVARUA_SIGNAL_TH);
        String str = TAG;
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Signal Threshhold: ");
        stringBuilder.append(rmssiThreshold);
        Log.d(str, stringBuilder.toString());
        if (FM_RX_RSSI_LEVEL_VERY_WEAK < rmssiThreshold && rmssiThreshold <= FM_RX_RSSI_LEVEL_WEAK) {
            signalStrength = FM_RX_RSSI_LEVEL_WEAK;
        } else if (FM_RX_RSSI_LEVEL_WEAK >= rmssiThreshold || rmssiThreshold > FM_RX_RSSI_LEVEL_STRONG) {
            signalStrength = FM_RX_RSSI_LEVEL_STRONG < rmssiThreshold ? FM_RX_RSSI_LEVEL_VERY_STRONG : FM_RX_RSSI_LEVEL_VERY_WEAK;
        } else {
            signalStrength = FM_RX_RSSI_LEVEL_STRONG;
        }
        if (signalStrength == FM_RX_RSSI_LEVEL_VERY_WEAK) {
            return 0;
        }
        if (signalStrength == FM_RX_RSSI_LEVEL_WEAK) {
            return 1;
        }
        if (signalStrength == FM_RX_RSSI_LEVEL_STRONG) {
            return 2;
        }
        if (signalStrength != FM_RX_RSSI_LEVEL_VERY_STRONG) {
            return 0;
        }
        return 3;
    }

    public boolean setRdsGroupOptions(int enRdsGrpsMask, int rdsBuffSize, boolean enRdsChangeFilter) {
        if (this.mRdsData.rdsOn(true) == 0 && this.mRdsData.rdsGrpOptions(enRdsGrpsMask, rdsBuffSize, enRdsChangeFilter) == 0) {
            return true;
        }
        return false;
    }

    public boolean registerRdsGroupProcessing(int fmGrpsToProc) {
        if (this.mRdsData.rdsOn(true) == 0 && this.mRdsData.rdsOptions(fmGrpsToProc) == 0) {
            return true;
        }
        return false;
    }

    public boolean enableAFjump(boolean enable) {
        if (this.mRdsData.rdsOn(true) != 0) {
            return false;
        }
        this.mRdsData.enableAFjump(enable);
        return true;
    }

    public int[] getStationList() {
        return this.mControl.stationList(sFd);
    }

    public int getRssi() {
        return FmReceiverWrapper.getRSSINative(sFd);
    }

    public boolean getInternalAntenna() {
        if (FmReceiverWrapper.getControlNative(sFd, V4L2_CID_PRIVATE_TAVARUA_ANTENNA) == 1) {
            return true;
        }
        return false;
    }

    public boolean setInternalAntenna(boolean intAnt) {
        int iAntenna;
        if (intAnt) {
            iAntenna = 1;
        } else {
            iAntenna = 0;
        }
        if (FmReceiverWrapper.setControlNative(sFd, V4L2_CID_PRIVATE_TAVARUA_ANTENNA, iAntenna) == 0) {
            return true;
        }
        return false;
    }

    public byte[] getRawRDS(int numBlocks) {
        if (numBlocks <= 0) {
            return null;
        }
        byte[] rawRds = new byte[(numBlocks * 3)];
        int re = FmReceiverWrapper.getRawRdsNative(sFd, rawRds, numBlocks * 3);
        if (re == numBlocks * 3) {
            return rawRds;
        }
        if (re <= 0) {
            return null;
        }
        byte[] buff = new byte[re];
        System.arraycopy(rawRds, 0, buff, 0, re);
        return buff;
    }

    public int getFMState() {
        return FmTransceiver.getFMPowerState();
    }

    public int getAudioQuilty(int value) {
        return FmReceiverWrapper.getAudioQuiltyNative(sFd, value);
    }

    public int setFmSnrThresh(int value) {
        return FmReceiverWrapper.setFmSnrThreshNative(sFd, value);
    }

    public int setFmRssiThresh(int value) {
        return FmReceiverWrapper.setFmRssiThreshNative(sFd, value);
    }

    public void setFmDeviceConnectionState(int state) {
        FmReceiverWrapper.setFmDeviceConnectionState(state);
    }
}