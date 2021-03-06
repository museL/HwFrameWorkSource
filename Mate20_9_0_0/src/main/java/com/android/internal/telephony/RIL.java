package com.android.internal.telephony;

import android.content.Context;
import android.hardware.radio.V1_0.ApnTypes;
import android.hardware.radio.V1_0.CallForwardInfo;
import android.hardware.radio.V1_0.Carrier;
import android.hardware.radio.V1_0.CarrierRestrictions;
import android.hardware.radio.V1_0.CdmaBroadcastSmsConfigInfo;
import android.hardware.radio.V1_0.CdmaSmsAck;
import android.hardware.radio.V1_0.CdmaSmsMessage;
import android.hardware.radio.V1_0.CdmaSmsSubaddress;
import android.hardware.radio.V1_0.CdmaSmsWriteArgs;
import android.hardware.radio.V1_0.CellInfoCdma;
import android.hardware.radio.V1_0.CellInfoGsm;
import android.hardware.radio.V1_0.CellInfoLte;
import android.hardware.radio.V1_0.CellInfoWcdma;
import android.hardware.radio.V1_0.DataProfileInfo;
import android.hardware.radio.V1_0.Dial;
import android.hardware.radio.V1_0.GsmBroadcastSmsConfigInfo;
import android.hardware.radio.V1_0.GsmSmsMessage;
import android.hardware.radio.V1_0.HardwareConfig;
import android.hardware.radio.V1_0.HardwareConfigModem;
import android.hardware.radio.V1_0.HardwareConfigSim;
import android.hardware.radio.V1_0.IRadio;
import android.hardware.radio.V1_0.IccIo;
import android.hardware.radio.V1_0.ImsSmsMessage;
import android.hardware.radio.V1_0.LceDataInfo;
import android.hardware.radio.V1_0.NvWriteItem;
import android.hardware.radio.V1_0.RadioCapability;
import android.hardware.radio.V1_0.RadioResponseInfo;
import android.hardware.radio.V1_0.SelectUiccSub;
import android.hardware.radio.V1_0.SimApdu;
import android.hardware.radio.V1_0.SmsWriteArgs;
import android.hardware.radio.V1_0.UusInfo;
import android.hardware.radio.V1_1.KeepaliveRequest;
import android.hardware.radio.V1_1.RadioAccessSpecifier;
import android.hardware.radio.V1_2.LinkCapacityEstimate;
import android.hardware.radio.deprecated.V1_0.IOemHook;
import android.net.ConnectivityManager;
import android.net.KeepalivePacketData;
import android.net.LinkProperties;
import android.os.AsyncResult;
import android.os.Build;
import android.os.Handler;
import android.os.IHwBinder.DeathRecipient;
import android.os.Message;
import android.os.Parcel;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.WorkSource;
import android.provider.Settings.System;
import android.service.carrier.CarrierIdentifier;
import android.telephony.CellIdentityCdma;
import android.telephony.CellInfo;
import android.telephony.CellSignalStrengthCdma;
import android.telephony.ClientRequestStats;
import android.telephony.ImsiEncryptionInfo;
import android.telephony.ModemActivityInfo;
import android.telephony.NeighboringCellInfo;
import android.telephony.NetworkScanRequest;
import android.telephony.PhoneNumberUtils;
import android.telephony.RadioAccessFamily;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyHistogram;
import android.telephony.data.DataProfile;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.CommandException.Error;
import com.android.internal.telephony.CommandsInterface.RadioState;
import com.android.internal.telephony.cat.ComprehensionTlv;
import com.android.internal.telephony.cat.ComprehensionTlvTag;
import com.android.internal.telephony.cdma.CdmaInformationRecords;
import com.android.internal.telephony.cdma.CdmaInformationRecords.CdmaDisplayInfoRec;
import com.android.internal.telephony.cdma.CdmaInformationRecords.CdmaLineControlInfoRec;
import com.android.internal.telephony.cdma.CdmaInformationRecords.CdmaNumberInfoRec;
import com.android.internal.telephony.cdma.CdmaInformationRecords.CdmaRedirectingNumberInfoRec;
import com.android.internal.telephony.cdma.CdmaInformationRecords.CdmaSignalInfoRec;
import com.android.internal.telephony.cdma.CdmaInformationRecords.CdmaT53AudioControlInfoRec;
import com.android.internal.telephony.cdma.CdmaInformationRecords.CdmaT53ClirInfoRec;
import com.android.internal.telephony.cdma.CdmaSmsBroadcastConfigInfo;
import com.android.internal.telephony.dataconnection.KeepaliveStatus;
import com.android.internal.telephony.gsm.SmsBroadcastConfigInfo;
import com.android.internal.telephony.metrics.TelephonyMetrics;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.util.Preconditions;
import com.google.android.mms.pdu.CharacterSets;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import vendor.huawei.hardware.hisiradio.V1_0.IHisiRadio;

public class RIL extends AbstractRIL implements CommandsInterface {
    private static final int DEFAULT_ACK_WAKE_LOCK_TIMEOUT_MS = 200;
    private static final int DEFAULT_BLOCKING_MESSAGE_RESPONSE_TIMEOUT_MS = 2000;
    private static final int DEFAULT_WAKE_LOCK_TIMEOUT_MS = 60000;
    static final String EMPTY_ALPHA_LONG = "";
    static final String EMPTY_ALPHA_SHORT = "";
    static final int EVENT_ACK_WAKE_LOCK_TIMEOUT = 4;
    static final int EVENT_BLOCKING_RESPONSE_TIMEOUT = 5;
    static final int EVENT_RADIO_PROXY_DEAD = 6;
    static final int EVENT_WAKE_LOCK_TIMEOUT = 2;
    public static final int FOR_ACK_WAKELOCK = 1;
    public static final int FOR_WAKELOCK = 0;
    static final String[] HIDL_SERVICE_NAME = new String[]{"slot1", "slot2", "slot3"};
    private static final int INT_SIZE = 4;
    public static final int INVALID_WAKELOCK = -1;
    static final int INVALTD_RADIO_TECH = -1;
    static final int IRADIO_GET_SERVICE_DELAY_MILLIS = (!HwModemCapability.isCapabilitySupport(9) ? 1000 : 4000);
    public static final String OEM_IDENTIFIER = "QOEMHOOK";
    private static final String PROP_LTE_ENABLED = "persist.radio.lte_enabled";
    static final String RILJ_ACK_WAKELOCK_NAME = "RILJ_ACK_WL";
    static final boolean RILJ_LOGD = true;
    static final boolean RILJ_LOGV = false;
    static final String RILJ_LOG_TAG = "RILJ";
    static final String RILJ_WAKELOCK_TAG = "*telephony-radio*";
    static final int RIL_HISTOGRAM_BUCKET_COUNT = 5;
    static SparseArray<TelephonyHistogram> mRilTimeHistograms = new SparseArray();
    final WakeLock mAckWakeLock;
    final int mAckWakeLockTimeout;
    volatile int mAckWlSequenceNum;
    protected WorkSource mActiveWakelockWorkSource;
    private final ClientWakelockTracker mClientWakelockTracker;
    int mHeaderSize;
    volatile IHisiRadio mHisiRadioProxy;
    boolean mIsMobileNetworkSupported;
    Object[] mLastNITZTimeInfo;
    int mLastRadioTech;
    private TelephonyMetrics mMetrics;
    OemHookIndication mOemHookIndication;
    volatile IOemHook mOemHookProxy;
    OemHookResponse mOemHookResponse;
    final Integer mPhoneId;
    protected WorkSource mRILDefaultWorkSource;
    RadioIndication mRadioIndication;
    volatile IRadio mRadioProxy;
    final AtomicLong mRadioProxyCookie;
    final RadioProxyDeathRecipient mRadioProxyDeathRecipient;
    RadioResponse mRadioResponse;
    SparseArray<RILRequest> mRequestList;
    final RilHandler mRilHandler;
    AtomicBoolean mTestingEmergencyCall;
    final WakeLock mWakeLock;
    int mWakeLockCount;
    final int mWakeLockTimeout;
    volatile int mWlSequenceNum;

    @VisibleForTesting
    public class RilHandler extends Handler {
        public void handleMessage(Message msg) {
            int i = msg.what;
            if (i != 2) {
                switch (i) {
                    case 4:
                        if (msg.arg1 == RIL.this.mAckWlSequenceNum) {
                            boolean access$000 = RIL.this.clearWakeLock(1);
                            return;
                        }
                        return;
                    case 5:
                        RILRequest rr = RIL.this.findAndRemoveRequestFromList(msg.arg1);
                        if (rr != null) {
                            if (rr.mResult != null) {
                                AsyncResult.forMessage(rr.mResult, RIL.getResponseForTimedOutRILRequest(rr), null);
                                rr.mResult.sendToTarget();
                                RIL.this.mMetrics.writeOnRilTimeoutResponse(RIL.this.mPhoneId.intValue(), rr.mSerial, rr.mRequest);
                            }
                            RIL.this.decrementWakeLock(rr);
                            rr.release();
                            return;
                        }
                        return;
                    case 6:
                        RIL ril = RIL.this;
                        StringBuilder stringBuilder = new StringBuilder();
                        stringBuilder.append("handleMessage: EVENT_RADIO_PROXY_DEAD cookie = ");
                        stringBuilder.append(msg.obj);
                        stringBuilder.append(" mRadioProxyCookie = ");
                        stringBuilder.append(RIL.this.mRadioProxyCookie.get());
                        ril.riljLog(stringBuilder.toString());
                        if (((Long) msg.obj).longValue() == RIL.this.mRadioProxyCookie.get()) {
                            RIL.this.resetProxyAndRequestList();
                            return;
                        }
                        return;
                    default:
                        return;
                }
            }
            synchronized (RIL.this.mRequestList) {
                if (msg.arg1 == RIL.this.mWlSequenceNum) {
                    int i2 = 0;
                    if (RIL.this.clearWakeLock(0)) {
                        int count = RIL.this.mRequestList.size();
                        String str = RIL.RILJ_LOG_TAG;
                        StringBuilder stringBuilder2 = new StringBuilder();
                        stringBuilder2.append("WAKE_LOCK_TIMEOUT  mRequestList=");
                        stringBuilder2.append(count);
                        Rlog.d(str, stringBuilder2.toString());
                        while (i2 < count) {
                            RILRequest rr2 = (RILRequest) RIL.this.mRequestList.valueAt(i2);
                            String str2 = RIL.RILJ_LOG_TAG;
                            StringBuilder stringBuilder3 = new StringBuilder();
                            stringBuilder3.append(i2);
                            stringBuilder3.append(": [");
                            stringBuilder3.append(rr2.mSerial);
                            stringBuilder3.append("] ");
                            stringBuilder3.append(RIL.requestToString(rr2.mRequest));
                            Rlog.d(str2, stringBuilder3.toString());
                            i2++;
                        }
                    }
                }
            }
        }
    }

    public static final class UnsolOemHookBuffer {
        private byte[] mData;
        private int mRilInstance;

        public UnsolOemHookBuffer(int rilInstance, byte[] data) {
            this.mRilInstance = rilInstance;
            if (data != null) {
                this.mData = new byte[data.length];
                System.arraycopy(data, 0, this.mData, 0, data.length);
                return;
            }
            this.mData = null;
        }

        public int getRilInstance() {
            return this.mRilInstance;
        }

        public byte[] getUnsolOemHookBuffer() {
            if (this.mData == null) {
                return null;
            }
            byte[] Data = new byte[this.mData.length];
            System.arraycopy(this.mData, 0, Data, 0, this.mData.length);
            return Data;
        }
    }

    final class RadioProxyDeathRecipient implements DeathRecipient {
        RadioProxyDeathRecipient() {
        }

        public void serviceDied(long cookie) {
            RIL.this.riljLog("serviceDied");
            RIL.this.mRilHandler.sendMessage(RIL.this.mRilHandler.obtainMessage(6, Long.valueOf(cookie)));
        }
    }

    public static List<TelephonyHistogram> getTelephonyRILTimingHistograms() {
        List<TelephonyHistogram> list;
        synchronized (mRilTimeHistograms) {
            list = new ArrayList(mRilTimeHistograms.size());
            for (int i = 0; i < mRilTimeHistograms.size(); i++) {
                list.add(new TelephonyHistogram((TelephonyHistogram) mRilTimeHistograms.valueAt(i)));
            }
        }
        return list;
    }

    private static Object getResponseForTimedOutRILRequest(RILRequest rr) {
        if (rr == null) {
            return null;
        }
        Object timeoutResponse = null;
        if (rr.mRequest == 135) {
            timeoutResponse = new ModemActivityInfo(0, 0, 0, new int[5], 0, 0);
        }
        return timeoutResponse;
    }

    private void resetProxyAndRequestList() {
        this.mRadioProxy = null;
        this.mHisiRadioProxy = null;
        clearHuaweiCommonRadioProxy();
        clearHwOemHookProxy();
        this.mOemHookProxy = null;
        this.mRadioProxyCookie.incrementAndGet();
        setRadioState(RadioState.RADIO_UNAVAILABLE);
        RILRequest.resetSerial();
        clearRequestList(1, false);
        getRadioProxy(null);
        getHuaweiCommonRadioProxy(null);
        getHisiRadioProxy(null);
        getHwOemHookProxy(null);
    }

    @VisibleForTesting
    public IRadio getRadioProxy(Message result) {
        if (!this.mIsMobileNetworkSupported) {
            if (result != null) {
                AsyncResult.forMessage(result, null, CommandException.fromRilErrno(1));
                result.sendToTarget();
            }
            return null;
        } else if (this.mRadioProxy != null) {
            return this.mRadioProxy;
        } else {
            try {
                this.mRadioProxy = IRadio.getService(HIDL_SERVICE_NAME[this.mPhoneId == null ? 0 : this.mPhoneId.intValue()], true);
                if (this.mRadioProxy != null) {
                    this.mRadioProxy.linkToDeath(this.mRadioProxyDeathRecipient, this.mRadioProxyCookie.incrementAndGet());
                    this.mRadioProxy.setResponseFunctions(this.mRadioResponse, this.mRadioIndication);
                } else {
                    riljLoge("getRadioProxy: mRadioProxy == null");
                }
            } catch (RemoteException | RuntimeException e) {
                this.mRadioProxy = null;
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("RadioProxy getService/setResponseFunctions: ");
                stringBuilder.append(e);
                riljLoge(stringBuilder.toString());
            }
            if (this.mRadioProxy == null) {
                riljLoge("getRadioProxy: mRadioProxy == null");
                if (result != null) {
                    AsyncResult.forMessage(result, null, CommandException.fromRilErrno(1));
                    result.sendToTarget();
                }
            }
            return this.mRadioProxy;
        }
    }

    @VisibleForTesting
    public IOemHook getOemHookProxy(Message result) {
        if (!this.mIsMobileNetworkSupported) {
            if (result != null) {
                AsyncResult.forMessage(result, null, CommandException.fromRilErrno(1));
                result.sendToTarget();
            }
            return null;
        } else if (this.mOemHookProxy != null) {
            return this.mOemHookProxy;
        } else {
            try {
                this.mOemHookProxy = IOemHook.getService(HIDL_SERVICE_NAME[this.mPhoneId == null ? 0 : this.mPhoneId.intValue()], true);
                if (this.mOemHookProxy != null) {
                    this.mOemHookProxy.setResponseFunctions(this.mOemHookResponse, this.mOemHookIndication);
                } else {
                    riljLoge("getOemHookProxy: mOemHookProxy == null");
                }
            } catch (RemoteException | RuntimeException e) {
                this.mOemHookProxy = null;
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("OemHookProxy getService/setResponseFunctions: ");
                stringBuilder.append(e);
                riljLoge(stringBuilder.toString());
            }
            if (this.mOemHookProxy == null && result != null) {
                AsyncResult.forMessage(result, null, CommandException.fromRilErrno(1));
                result.sendToTarget();
            }
            return this.mOemHookProxy;
        }
    }

    public RIL(Context context, int preferredNetworkType, int cdmaSubscription) {
        this(context, preferredNetworkType, cdmaSubscription, null);
    }

    public RIL(Context context, int preferredNetworkType, int cdmaSubscription, Integer instanceId) {
        super(context);
        this.mLastRadioTech = -1;
        this.mHeaderSize = OEM_IDENTIFIER.length() + 8;
        this.mClientWakelockTracker = new ClientWakelockTracker();
        this.mWlSequenceNum = 0;
        this.mAckWlSequenceNum = 0;
        this.mRequestList = new SparseArray();
        this.mTestingEmergencyCall = new AtomicBoolean(false);
        this.mMetrics = TelephonyMetrics.getInstance();
        this.mRadioProxy = null;
        this.mHisiRadioProxy = null;
        this.mOemHookProxy = null;
        this.mRadioProxyCookie = new AtomicLong(0);
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("RIL: init preferredNetworkType=");
        stringBuilder.append(preferredNetworkType);
        stringBuilder.append(" cdmaSubscription=");
        stringBuilder.append(cdmaSubscription);
        stringBuilder.append(")");
        riljLog(stringBuilder.toString());
        this.mContext = context;
        this.mCdmaSubscription = cdmaSubscription;
        this.mPreferredNetworkType = preferredNetworkType;
        this.mPhoneType = 0;
        this.mPhoneId = instanceId;
        this.mIsMobileNetworkSupported = ((ConnectivityManager) context.getSystemService("connectivity")).isNetworkSupported(0);
        if (this.mPhoneId != null) {
            setHwRILReferenceInstanceId(this.mPhoneId.intValue());
        }
        this.mRadioResponse = new RadioResponse(this);
        this.mRadioIndication = new RadioIndication(this);
        this.mOemHookResponse = new OemHookResponse(this);
        this.mOemHookIndication = new OemHookIndication(this);
        this.mRilHandler = new RilHandler();
        this.mRadioProxyDeathRecipient = new RadioProxyDeathRecipient();
        PowerManager pm = (PowerManager) context.getSystemService("power");
        this.mWakeLock = pm.newWakeLock(1, RILJ_WAKELOCK_TAG);
        this.mWakeLock.setReferenceCounted(false);
        this.mAckWakeLock = pm.newWakeLock(1, RILJ_ACK_WAKELOCK_NAME);
        this.mAckWakeLock.setReferenceCounted(false);
        this.mWakeLockTimeout = SystemProperties.getInt("ro.ril.wake_lock_timeout", 60000);
        this.mAckWakeLockTimeout = SystemProperties.getInt("ro.ril.wake_lock_timeout", 200);
        this.mWakeLockCount = 0;
        this.mRILDefaultWorkSource = new WorkSource(context.getApplicationInfo().uid, context.getPackageName());
        TelephonyDevController tdc = TelephonyDevController.getInstance();
        TelephonyDevController.registerRIL(this);
        getRadioProxy(null);
        getHwOemHookProxy(null);
    }

    public void setOnNITZTime(Handler h, int what, Object obj) {
        super.setOnNITZTime(h, what, obj);
        if (this.mLastNITZTimeInfo != null) {
            this.mNITZTimeRegistrant.notifyRegistrant(new AsyncResult(null, this.mLastNITZTimeInfo, null));
        }
    }

    private void addRequest(RILRequest rr) {
        acquireWakeLock(rr, 0);
        synchronized (this.mRequestList) {
            rr.mStartTimeMs = SystemClock.elapsedRealtime();
            this.mRequestList.append(rr.mSerial, rr);
        }
    }

    private RILRequest obtainRequest(int request, Message result, WorkSource workSource) {
        RILRequest rr = RILRequest.obtain(request, result, workSource);
        addRequest(rr);
        return rr;
    }

    private void handleRadioProxyExceptionForRR(RILRequest rr, String caller, Exception e) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(caller);
        stringBuilder.append(": ");
        stringBuilder.append(e);
        riljLoge(stringBuilder.toString());
        resetProxyAndRequestList();
    }

    private String convertNullToEmptyString(String string) {
        return string != null ? string : "";
    }

    public void getIccCardStatus(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(1, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.getIccCardStatus(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "getIccCardStatus", e);
            }
        }
    }

    public void getIccSlotsStatus(Message result) {
        if (result != null) {
            AsyncResult.forMessage(result, null, CommandException.fromRilErrno(6));
            result.sendToTarget();
        }
    }

    public void setLogicalToPhysicalSlotMapping(int[] physicalSlots, Message result) {
        if (result != null) {
            AsyncResult.forMessage(result, null, CommandException.fromRilErrno(6));
            result.sendToTarget();
        }
    }

    public void supplyIccPin(String pin, Message result) {
        supplyIccPinForApp(pin, null, result);
    }

    public void supplyIccPinForApp(String pin, String aid, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(2, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" aid = ");
            stringBuilder.append(aid);
            riljLog(stringBuilder.toString());
            try {
                radioProxy.supplyIccPinForApp(rr.mSerial, convertNullToEmptyString(pin), convertNullToEmptyString(aid));
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "supplyIccPinForApp", e);
            }
        }
    }

    public void supplyIccPuk(String puk, String newPin, Message result) {
        supplyIccPukForApp(puk, newPin, null, result);
    }

    public void supplyIccPukForApp(String puk, String newPin, String aid, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(3, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" aid = ");
            stringBuilder.append(aid);
            riljLog(stringBuilder.toString());
            try {
                radioProxy.supplyIccPukForApp(rr.mSerial, convertNullToEmptyString(puk), convertNullToEmptyString(newPin), convertNullToEmptyString(aid));
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "supplyIccPukForApp", e);
            }
        }
    }

    public void supplyIccPin2(String pin, Message result) {
        supplyIccPin2ForApp(pin, null, result);
    }

    public void supplyIccPin2ForApp(String pin, String aid, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(4, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" aid = ");
            stringBuilder.append(aid);
            riljLog(stringBuilder.toString());
            try {
                radioProxy.supplyIccPin2ForApp(rr.mSerial, convertNullToEmptyString(pin), convertNullToEmptyString(aid));
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "supplyIccPin2ForApp", e);
            }
        }
    }

    public void supplyIccPuk2(String puk2, String newPin2, Message result) {
        supplyIccPuk2ForApp(puk2, newPin2, null, result);
    }

    public void supplyIccPuk2ForApp(String puk, String newPin2, String aid, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(5, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" aid = ");
            stringBuilder.append(aid);
            riljLog(stringBuilder.toString());
            try {
                radioProxy.supplyIccPuk2ForApp(rr.mSerial, convertNullToEmptyString(puk), convertNullToEmptyString(newPin2), convertNullToEmptyString(aid));
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "supplyIccPuk2ForApp", e);
            }
        }
    }

    public void changeIccPin(String oldPin, String newPin, Message result) {
        changeIccPinForApp(oldPin, newPin, null, result);
    }

    public void changeIccPinForApp(String oldPin, String newPin, String aid, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(6, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" oldPin = ");
            stringBuilder.append(oldPin != null ? oldPin.replaceAll("\\d", CharacterSets.MIMENAME_ANY_CHARSET) : "");
            stringBuilder.append(" newPin = ");
            stringBuilder.append(newPin != null ? newPin.replaceAll("\\d", CharacterSets.MIMENAME_ANY_CHARSET) : "");
            stringBuilder.append(" aid = ");
            stringBuilder.append(aid);
            riljLog(stringBuilder.toString());
            try {
                radioProxy.changeIccPinForApp(rr.mSerial, convertNullToEmptyString(oldPin), convertNullToEmptyString(newPin), convertNullToEmptyString(aid));
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "changeIccPinForApp", e);
            }
        }
    }

    public void changeIccPin2(String oldPin2, String newPin2, Message result) {
        changeIccPin2ForApp(oldPin2, newPin2, null, result);
    }

    public void changeIccPin2ForApp(String oldPin2, String newPin2, String aid, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(7, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" oldPin = ");
            stringBuilder.append(oldPin2 != null ? oldPin2.replaceAll("\\d", CharacterSets.MIMENAME_ANY_CHARSET) : "");
            stringBuilder.append(" newPin = ");
            stringBuilder.append(newPin2 != null ? newPin2.replaceAll("\\d", CharacterSets.MIMENAME_ANY_CHARSET) : "");
            stringBuilder.append(" aid = ");
            stringBuilder.append(aid);
            riljLog(stringBuilder.toString());
            try {
                radioProxy.changeIccPin2ForApp(rr.mSerial, convertNullToEmptyString(oldPin2), convertNullToEmptyString(newPin2), convertNullToEmptyString(aid));
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "changeIccPin2ForApp", e);
            }
        }
    }

    public void supplyNetworkDepersonalization(String netpin, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(8, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" netpin = ");
            stringBuilder.append(netpin);
            riljLog(stringBuilder.toString());
            try {
                radioProxy.supplyNetworkDepersonalization(rr.mSerial, convertNullToEmptyString(netpin));
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "supplyNetworkDepersonalization", e);
            }
        }
    }

    public void getCurrentCalls(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(9, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.getCurrentCalls(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "getCurrentCalls", e);
            }
        }
    }

    public void dial(String address, int clirMode, Message result) {
        dial(address, clirMode, null, result);
    }

    public void dial(String address, int clirMode, UUSInfo uusInfo, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(10, result, this.mRILDefaultWorkSource);
            Dial dialInfo = new Dial();
            dialInfo.address = convertNullToEmptyString(address);
            dialInfo.clir = clirMode;
            if (uusInfo != null) {
                UusInfo info = new UusInfo();
                info.uusType = uusInfo.getType();
                info.uusDcs = uusInfo.getDcs();
                info.uusData = new String(uusInfo.getUserData());
                dialInfo.uusInfo.add(info);
            }
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.dial(rr.mSerial, dialInfo);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "dial", e);
            }
        }
    }

    public void getIMSI(Message result) {
        getIMSIForApp(null, result);
    }

    public void getIMSIForApp(String aid, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(11, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append(">  ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" aid = ");
            stringBuilder.append(aid);
            riljLog(stringBuilder.toString());
            try {
                radioProxy.getImsiForApp(rr.mSerial, convertNullToEmptyString(aid));
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "getIMSIForApp", e);
            }
        }
    }

    public void hangupConnection(int gsmIndex, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(12, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" gsmIndex = ");
            stringBuilder.append(gsmIndex);
            riljLog(stringBuilder.toString());
            try {
                radioProxy.hangup(rr.mSerial, gsmIndex);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "hangupConnection", e);
            }
        }
    }

    public void hangupWaitingOrBackground(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(13, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.hangupWaitingOrBackground(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "hangupWaitingOrBackground", e);
            }
        }
    }

    public void hangupForegroundResumeBackground(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(14, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.hangupForegroundResumeBackground(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "hangupForegroundResumeBackground", e);
            }
        }
    }

    public void switchWaitingOrHoldingAndActive(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(15, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.switchWaitingOrHoldingAndActive(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "switchWaitingOrHoldingAndActive", e);
            }
        }
    }

    public void conference(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(16, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.conference(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "conference", e);
            }
        }
    }

    public void rejectCall(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(17, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.rejectCall(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "rejectCall", e);
            }
        }
    }

    public void getLastCallFailCause(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(18, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.getLastCallFailCause(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "getLastCallFailCause", e);
            }
        }
    }

    public void getSignalStrength(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(19, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.getSignalStrength(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "getSignalStrength", e);
            }
        }
    }

    public void getVoiceRegistrationState(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(20, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.getVoiceRegistrationState(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "getVoiceRegistrationState", e);
            }
        }
    }

    public void getDataRegistrationState(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(21, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.getDataRegistrationState(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "getDataRegistrationState", e);
            }
        }
    }

    public void getOperator(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(22, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.getOperator(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "getOperator", e);
            }
        }
    }

    public void setRadioPower(boolean on, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            if (ServiceStateTracker.ISDEMO) {
                on = false;
            }
            setShouldReportRoamingPlusInfo(on);
            RILRequest rr = obtainRequest(23, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" on = ");
            stringBuilder.append(on);
            riljLog(stringBuilder.toString());
            try {
                radioProxy.setRadioPower(rr.mSerial, on);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "setRadioPower", e);
            }
        }
    }

    public void sendDtmf(char c, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(24, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                int i = rr.mSerial;
                StringBuilder stringBuilder2 = new StringBuilder();
                stringBuilder2.append(c);
                stringBuilder2.append("");
                radioProxy.sendDtmf(i, stringBuilder2.toString());
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "sendDtmf", e);
            }
        }
    }

    private GsmSmsMessage constructGsmSendSmsRilRequest(String smscPdu, String pdu) {
        GsmSmsMessage msg = new GsmSmsMessage();
        msg.smscPdu = smscPdu == null ? "" : smscPdu;
        msg.pdu = pdu == null ? "" : pdu;
        return msg;
    }

    public void sendSMS(String smscPdu, String pdu, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(25, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.sendSms(rr.mSerial, constructGsmSendSmsRilRequest(smscPdu, pdu));
                this.mMetrics.writeRilSendSms(this.mPhoneId.intValue(), rr.mSerial, 1, 1);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "sendSMS", e);
            }
        }
    }

    public void sendSMSExpectMore(String smscPdu, String pdu, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(26, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.sendSMSExpectMore(rr.mSerial, constructGsmSendSmsRilRequest(smscPdu, pdu));
                this.mMetrics.writeRilSendSms(this.mPhoneId.intValue(), rr.mSerial, 1, 1);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "sendSMSExpectMore", e);
            }
        }
    }

    /* JADX WARNING: Removed duplicated region for block: B:17:0x0039 A:{RETURN} */
    /* JADX WARNING: Removed duplicated region for block: B:21:0x003d A:{RETURN} */
    /* JADX WARNING: Removed duplicated region for block: B:20:0x003c A:{RETURN} */
    /* JADX WARNING: Removed duplicated region for block: B:18:0x003a  */
    /* JADX WARNING: Removed duplicated region for block: B:17:0x0039 A:{RETURN} */
    /* JADX WARNING: Removed duplicated region for block: B:21:0x003d A:{RETURN} */
    /* JADX WARNING: Removed duplicated region for block: B:20:0x003c A:{RETURN} */
    /* JADX WARNING: Removed duplicated region for block: B:18:0x003a  */
    /* JADX WARNING: Removed duplicated region for block: B:17:0x0039 A:{RETURN} */
    /* JADX WARNING: Removed duplicated region for block: B:21:0x003d A:{RETURN} */
    /* JADX WARNING: Removed duplicated region for block: B:20:0x003c A:{RETURN} */
    /* JADX WARNING: Removed duplicated region for block: B:18:0x003a  */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private static int convertToHalMvnoType(String mvnoType) {
        int hashCode = mvnoType.hashCode();
        if (hashCode == 102338) {
            if (mvnoType.equals("gid")) {
                hashCode = 1;
                switch (hashCode) {
                    case 0:
                        break;
                    case 1:
                        break;
                    case 2:
                        break;
                    default:
                        break;
                }
            }
        } else if (hashCode == 114097) {
            if (mvnoType.equals("spn")) {
                hashCode = 2;
                switch (hashCode) {
                    case 0:
                        break;
                    case 1:
                        break;
                    case 2:
                        break;
                    default:
                        break;
                }
            }
        } else if (hashCode == 3236474 && mvnoType.equals("imsi")) {
            hashCode = 0;
            switch (hashCode) {
                case 0:
                    return 1;
                case 1:
                    return 2;
                case 2:
                    return 3;
                default:
                    return 0;
            }
        }
        hashCode = -1;
        switch (hashCode) {
            case 0:
                break;
            case 1:
                break;
            case 2:
                break;
            default:
                break;
        }
    }

    private static DataProfileInfo convertToHalDataProfile(DataProfile dp) {
        DataProfileInfo dpi = new DataProfileInfo();
        dpi.profileId = dp.getProfileId();
        dpi.apn = dp.getApn();
        dpi.protocol = dp.getProtocol();
        dpi.roamingProtocol = dp.getRoamingProtocol();
        dpi.authType = dp.getAuthType();
        dpi.user = dp.getUserName();
        dpi.password = dp.getPassword();
        dpi.type = dp.getType();
        dpi.maxConnsTime = dp.getMaxConnsTime();
        dpi.maxConns = dp.getMaxConns();
        dpi.waitTime = dp.getWaitTime();
        dpi.enabled = dp.isEnabled();
        dpi.supportedApnTypesBitmap = dp.getSupportedApnTypesBitmap();
        dpi.bearerBitmap = dp.getBearerBitmap();
        dpi.mtu = dp.getMtu();
        dpi.mvnoType = convertToHalMvnoType(dp.getMvnoType());
        dpi.mvnoMatchData = dp.getMvnoMatchData();
        return dpi;
    }

    private static int convertToHalResetNvType(int resetType) {
        switch (resetType) {
            case 1:
                return 0;
            case 2:
                return 1;
            case 3:
                return 2;
            default:
                return -1;
        }
    }

    public void setupDataCall(int accessNetworkType, DataProfile dataProfile, boolean isRoaming, boolean allowRoaming, int reason, LinkProperties linkProperties, Message result) {
        StringBuilder stringBuilder;
        Exception e;
        RILRequest rILRequest;
        boolean z = isRoaming;
        boolean z2 = allowRoaming;
        Message message = result;
        IRadio radioProxy = getRadioProxy(message);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(27, message, this.mRILDefaultWorkSource);
            DataProfile dp = dataProfile;
            try {
                Map<String, String> map = correctApnAuth(dp.getUserName(), dp.getAuthType(), dp.getPassword());
                dp.setUserName((String) map.get("userName"));
                dp.setPassword((String) map.get("password"));
                dp.setAuthType(Integer.parseInt((String) map.get("authType")));
            } catch (Exception e2) {
                stringBuilder = new StringBuilder();
                stringBuilder.append(e2);
                stringBuilder.append("The authType is not number");
                riljLog(stringBuilder.toString());
            }
            DataProfileInfo dpi = convertToHalDataProfile(dp);
            android.hardware.radio.V1_2.IRadio radioProxy12 = android.hardware.radio.V1_2.IRadio.castFrom(radioProxy);
            DataProfile dataProfile2;
            if (radioProxy12 == null) {
                int dataRat = 0;
                try {
                    Phone phone = PhoneFactory.getPhone(this.mPhoneId.intValue());
                    if (phone != null) {
                        ServiceState ss = phone.getServiceState();
                        if (ss != null) {
                            dataRat = ss.getRilDataRadioTechnology();
                        }
                    }
                    stringBuilder = new StringBuilder();
                    stringBuilder.append(rr.serialString());
                    stringBuilder.append("> ");
                    stringBuilder.append(requestToString(rr.mRequest));
                    stringBuilder.append(",dataRat=");
                    stringBuilder.append(dataRat);
                    stringBuilder.append(",isRoaming=");
                    stringBuilder.append(z);
                    stringBuilder.append(",allowRoaming=");
                    stringBuilder.append(z2);
                    stringBuilder.append(",");
                    stringBuilder.append(dp);
                    riljLog(stringBuilder.toString());
                    radioProxy.setupDataCall(rr.mSerial, dataRat, dpi, dp.isModemCognitive(), z2, z);
                    dataProfile2 = dp;
                    rILRequest = rr;
                    return;
                } catch (RemoteException | RuntimeException e3) {
                    e2 = e3;
                    dataProfile2 = dp;
                    rILRequest = rr;
                    handleRadioProxyExceptionForRR(rILRequest, "setupDataCall", e2);
                }
            }
            try {
                ArrayList<String> addresses = new ArrayList();
                ArrayList<String> dnses = new ArrayList();
                if (linkProperties != null) {
                    for (InetAddress address : linkProperties.getAddresses()) {
                        addresses.add(address.getHostAddress());
                    }
                    for (InetAddress address2 : linkProperties.getDnsServers()) {
                        dnses.add(address2.getHostAddress());
                    }
                }
                stringBuilder = new StringBuilder();
                stringBuilder.append(rr.serialString());
                stringBuilder.append("> ");
                stringBuilder.append(requestToString(rr.mRequest));
                stringBuilder.append(",accessNetworkType=");
                int i = accessNetworkType;
                stringBuilder.append(i);
                stringBuilder.append(",isRoaming=");
                stringBuilder.append(z);
                stringBuilder.append(",allowRoaming=");
                stringBuilder.append(z2);
                stringBuilder.append(",");
                stringBuilder.append(dp);
                stringBuilder.append(",addresses=");
                stringBuilder.append(addresses);
                stringBuilder.append(",dnses=");
                stringBuilder.append(dnses);
                riljLog(stringBuilder.toString());
                int i2 = i;
                boolean z3 = z;
                rILRequest = rr;
                try {
                    radioProxy12.setupDataCall_1_2(rr.mSerial, i2, dpi, dp.isModemCognitive(), z2, z3, reason, addresses, dnses);
                } catch (RemoteException | RuntimeException e4) {
                    e2 = e4;
                }
            } catch (RemoteException | RuntimeException e5) {
                e2 = e5;
                dataProfile2 = dp;
                rILRequest = rr;
                handleRadioProxyExceptionForRR(rILRequest, "setupDataCall", e2);
            }
        }
    }

    public void iccIO(int command, int fileId, String path, int p1, int p2, int p3, String data, String pin2, Message result) {
        iccIOForApp(command, fileId, path, p1, p2, p3, data, pin2, null, result);
    }

    public void iccIOForApp(int command, int fileId, String path, int p1, int p2, int p3, String data, String pin2, String aid, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(28, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder;
            if (Build.IS_DEBUGGABLE) {
                stringBuilder = new StringBuilder();
                stringBuilder.append(rr.serialString());
                stringBuilder.append("> iccIO: ");
                stringBuilder.append(requestToString(rr.mRequest));
                stringBuilder.append(" command = 0x");
                stringBuilder.append(Integer.toHexString(command));
                stringBuilder.append(" fileId = 0x");
                stringBuilder.append(Integer.toHexString(fileId));
                stringBuilder.append(" path = ");
                stringBuilder.append(path);
                stringBuilder.append(" p1 = ");
                stringBuilder.append(p1);
                stringBuilder.append(" p2 = ");
                stringBuilder.append(p2);
                stringBuilder.append(" p3 =  data = ");
                stringBuilder.append(data);
                stringBuilder.append(" aid = ");
                stringBuilder.append(aid);
                riljLog(stringBuilder.toString());
            } else {
                stringBuilder = new StringBuilder();
                stringBuilder.append(rr.serialString());
                stringBuilder.append("> iccIO: ");
                stringBuilder.append(requestToString(rr.mRequest));
                riljLog(stringBuilder.toString());
            }
            IccIo iccIo = new IccIo();
            iccIo.command = command;
            iccIo.fileId = fileId;
            iccIo.path = convertNullToEmptyString(path);
            iccIo.p1 = p1;
            iccIo.p2 = p2;
            iccIo.p3 = p3;
            iccIo.data = convertNullToEmptyString(data);
            iccIo.pin2 = convertNullToEmptyString(pin2);
            iccIo.aid = convertNullToEmptyString(aid);
            try {
                radioProxy.iccIOForApp(rr.mSerial, iccIo);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "iccIOForApp", e);
            }
        }
    }

    public void sendUSSD(String ussd, Message result) {
        HwTelephonyFactory.getHwChrServiceManager().reportCallException("Telephony", this.mPhoneId.intValue(), 0, "AP_FLOW_SUC");
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(29, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" ussd = ");
            stringBuilder.append("*******");
            riljLog(stringBuilder.toString());
            try {
                radioProxy.sendUssd(rr.mSerial, convertNullToEmptyString(ussd));
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "sendUSSD", e);
            }
        }
    }

    public void cancelPendingUssd(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(30, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.cancelPendingUssd(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "cancelPendingUssd", e);
            }
        }
    }

    public void getCLIR(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(31, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.getClir(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "getCLIR", e);
            }
        }
    }

    public void setCLIR(int clirMode, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(32, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" clirMode = ");
            stringBuilder.append(clirMode);
            riljLog(stringBuilder.toString());
            try {
                radioProxy.setClir(rr.mSerial, clirMode);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "setCLIR", e);
            }
        }
    }

    public void queryCallForwardStatus(int cfReason, int serviceClass, String number, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(33, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" cfreason = ");
            stringBuilder.append(cfReason);
            stringBuilder.append(" serviceClass = ");
            stringBuilder.append(serviceClass);
            riljLog(stringBuilder.toString());
            CallForwardInfo cfInfo = new CallForwardInfo();
            cfInfo.reason = cfReason;
            cfInfo.serviceClass = serviceClass;
            cfInfo.toa = PhoneNumberUtils.toaFromString(number);
            cfInfo.number = convertNullToEmptyString(number);
            cfInfo.timeSeconds = 0;
            try {
                radioProxy.getCallForwardStatus(rr.mSerial, cfInfo);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "queryCallForwardStatus", e);
            }
        }
    }

    public void setCallForward(int action, int cfReason, int serviceClass, String number, int timeSeconds, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(34, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" action = ");
            stringBuilder.append(action);
            stringBuilder.append(" cfReason = ");
            stringBuilder.append(cfReason);
            stringBuilder.append(" serviceClass = ");
            stringBuilder.append(serviceClass);
            stringBuilder.append(" timeSeconds = ");
            stringBuilder.append(timeSeconds);
            riljLog(stringBuilder.toString());
            CallForwardInfo cfInfo = new CallForwardInfo();
            cfInfo.status = action;
            cfInfo.reason = cfReason;
            cfInfo.serviceClass = serviceClass;
            cfInfo.toa = PhoneNumberUtils.toaFromString(number);
            cfInfo.number = convertNullToEmptyString(number);
            cfInfo.timeSeconds = timeSeconds;
            try {
                radioProxy.setCallForward(rr.mSerial, cfInfo);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "setCallForward", e);
            }
        }
    }

    public void queryCallWaiting(int serviceClass, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(35, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" serviceClass = ");
            stringBuilder.append(serviceClass);
            riljLog(stringBuilder.toString());
            try {
                radioProxy.getCallWaiting(rr.mSerial, serviceClass);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "queryCallWaiting", e);
            }
        }
    }

    public void setCallWaiting(boolean enable, int serviceClass, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(36, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" enable = ");
            stringBuilder.append(enable);
            stringBuilder.append(" serviceClass = ");
            stringBuilder.append(serviceClass);
            riljLog(stringBuilder.toString());
            try {
                radioProxy.setCallWaiting(rr.mSerial, enable, serviceClass);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "setCallWaiting", e);
            }
        }
    }

    public void acknowledgeLastIncomingGsmSms(boolean success, int cause, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(37, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" success = ");
            stringBuilder.append(success);
            stringBuilder.append(" cause = ");
            stringBuilder.append(cause);
            riljLog(stringBuilder.toString());
            try {
                radioProxy.acknowledgeLastIncomingGsmSms(rr.mSerial, success, cause);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "acknowledgeLastIncomingGsmSms", e);
            }
        }
    }

    public void acceptCall(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(40, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.acceptCall(rr.mSerial);
                this.mMetrics.writeRilAnswer(this.mPhoneId.intValue(), rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "acceptCall", e);
            }
        }
    }

    public void deactivateDataCall(int cid, int reason, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(41, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" cid = ");
            stringBuilder.append(cid);
            stringBuilder.append(" reason = ");
            stringBuilder.append(reason);
            riljLog(stringBuilder.toString());
            android.hardware.radio.V1_2.IRadio radioProxy12 = android.hardware.radio.V1_2.IRadio.castFrom(radioProxy);
            if (radioProxy12 == null) {
                try {
                    radioProxy.deactivateDataCall(rr.mSerial, cid, reason == 2);
                } catch (RemoteException | RuntimeException e) {
                    handleRadioProxyExceptionForRR(rr, "deactivateDataCall", e);
                    return;
                }
            }
            radioProxy12.deactivateDataCall_1_2(rr.mSerial, cid, reason);
            this.mMetrics.writeRilDeactivateDataCall(this.mPhoneId.intValue(), rr.mSerial, cid, reason);
        }
    }

    public void queryFacilityLock(String facility, String password, int serviceClass, Message result) {
        queryFacilityLockForApp(facility, password, serviceClass, null, result);
    }

    public void queryFacilityLockForApp(String facility, String password, int serviceClass, String appId, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(42, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" facility = ");
            stringBuilder.append(facility);
            stringBuilder.append(" serviceClass = ");
            stringBuilder.append(serviceClass);
            stringBuilder.append(" appId = ");
            stringBuilder.append(appId);
            riljLog(stringBuilder.toString());
            try {
                radioProxy.getFacilityLockForApp(rr.mSerial, convertNullToEmptyString(facility), convertNullToEmptyString(password), serviceClass, convertNullToEmptyString(appId));
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "getFacilityLockForApp", e);
            }
        }
    }

    public void setFacilityLock(String facility, boolean lockState, String password, int serviceClass, Message result) {
        setFacilityLockForApp(facility, lockState, password, serviceClass, null, result);
    }

    public void setFacilityLockForApp(String facility, boolean lockState, String password, int serviceClass, String appId, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(43, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" facility = ");
            stringBuilder.append(facility);
            stringBuilder.append(" lockstate = ");
            stringBuilder.append(lockState);
            stringBuilder.append(" serviceClass = ");
            stringBuilder.append(serviceClass);
            stringBuilder.append(" appId = ");
            stringBuilder.append(appId);
            riljLog(stringBuilder.toString());
            try {
                radioProxy.setFacilityLockForApp(rr.mSerial, convertNullToEmptyString(facility), lockState, convertNullToEmptyString(password), serviceClass, convertNullToEmptyString(appId));
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "setFacilityLockForApp", e);
            }
        }
    }

    public void changeBarringPassword(String facility, String oldPwd, String newPwd, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(44, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append("facility = ");
            stringBuilder.append(facility);
            riljLog(stringBuilder.toString());
            try {
                radioProxy.setBarringPassword(rr.mSerial, convertNullToEmptyString(facility), convertNullToEmptyString(oldPwd), convertNullToEmptyString(newPwd));
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "changeBarringPassword", e);
            }
        }
    }

    public void getNetworkSelectionMode(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(45, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.getNetworkSelectionMode(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "getNetworkSelectionMode", e);
            }
        }
    }

    public void setNetworkSelectionModeAutomatic(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(46, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.setNetworkSelectionModeAutomatic(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "setNetworkSelectionModeAutomatic", e);
            }
        }
    }

    public void setNetworkSelectionModeManual(String operatorNumeric, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(47, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" operatorNumeric = ");
            stringBuilder.append(operatorNumeric);
            riljLog(stringBuilder.toString());
            try {
                radioProxy.setNetworkSelectionModeManual(rr.mSerial, convertNullToEmptyString(operatorNumeric));
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "setNetworkSelectionModeManual", e);
            }
        }
    }

    public void getAvailableNetworks(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(48, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.getAvailableNetworks(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "getAvailableNetworks", e);
            }
        }
    }

    private RadioAccessSpecifier convertRadioAccessSpecifierToRadioHAL(android.telephony.RadioAccessSpecifier ras) {
        List<Integer> bands;
        int length;
        RadioAccessSpecifier rasInHalFormat = new RadioAccessSpecifier();
        rasInHalFormat.radioAccessNetwork = ras.getRadioAccessNetwork();
        switch (ras.getRadioAccessNetwork()) {
            case 1:
                bands = rasInHalFormat.geranBands;
                break;
            case 2:
                bands = rasInHalFormat.utranBands;
                break;
            case 3:
                bands = rasInHalFormat.eutranBands;
                break;
            default:
                String str = RILJ_LOG_TAG;
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("radioAccessNetwork ");
                stringBuilder.append(ras.getRadioAccessNetwork());
                stringBuilder.append(" not supported!");
                Log.wtf(str, stringBuilder.toString());
                return null;
        }
        int i = 0;
        if (ras.getBands() != null) {
            for (int band : ras.getBands()) {
                bands.add(Integer.valueOf(band));
            }
        }
        if (ras.getChannels() != null) {
            int[] channels = ras.getChannels();
            length = channels.length;
            while (i < length) {
                rasInHalFormat.channels.add(Integer.valueOf(channels[i]));
                i++;
            }
        }
        return rasInHalFormat;
    }

    public void startNetworkScan(NetworkScanRequest nsr, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            android.hardware.radio.V1_2.IRadio radioProxy12 = android.hardware.radio.V1_2.IRadio.castFrom(radioProxy);
            int i = 0;
            RILRequest rr;
            StringBuilder stringBuilder;
            if (radioProxy12 != null) {
                android.hardware.radio.V1_2.NetworkScanRequest request = new android.hardware.radio.V1_2.NetworkScanRequest();
                request.type = nsr.getScanType();
                request.interval = nsr.getSearchPeriodicity();
                request.maxSearchTime = nsr.getMaxSearchTime();
                request.incrementalResultsPeriodicity = nsr.getIncrementalResultsPeriodicity();
                request.incrementalResults = nsr.getIncrementalResults();
                android.telephony.RadioAccessSpecifier[] specifiers = nsr.getSpecifiers();
                int length = specifiers.length;
                while (i < length) {
                    RadioAccessSpecifier rasInHalFormat = convertRadioAccessSpecifierToRadioHAL(specifiers[i]);
                    if (rasInHalFormat != null) {
                        request.specifiers.add(rasInHalFormat);
                        i++;
                    } else {
                        return;
                    }
                }
                request.mccMncs.addAll(nsr.getPlmns());
                rr = obtainRequest(142, result, this.mRILDefaultWorkSource);
                stringBuilder = new StringBuilder();
                stringBuilder.append(rr.serialString());
                stringBuilder.append("> ");
                stringBuilder.append(requestToString(rr.mRequest));
                riljLog(stringBuilder.toString());
                try {
                    radioProxy12.startNetworkScan_1_2(rr.mSerial, request);
                } catch (RemoteException | RuntimeException e) {
                    handleRadioProxyExceptionForRR(rr, "startNetworkScan", e);
                }
            } else {
                android.hardware.radio.V1_1.IRadio radioProxy11 = android.hardware.radio.V1_1.IRadio.castFrom(radioProxy);
                if (radioProxy11 != null) {
                    android.hardware.radio.V1_1.NetworkScanRequest request2 = new android.hardware.radio.V1_1.NetworkScanRequest();
                    request2.type = nsr.getScanType();
                    request2.interval = nsr.getSearchPeriodicity();
                    android.telephony.RadioAccessSpecifier[] specifiers2 = nsr.getSpecifiers();
                    int length2 = specifiers2.length;
                    while (i < length2) {
                        RadioAccessSpecifier rasInHalFormat2 = convertRadioAccessSpecifierToRadioHAL(specifiers2[i]);
                        if (rasInHalFormat2 != null) {
                            request2.specifiers.add(rasInHalFormat2);
                            i++;
                        } else {
                            return;
                        }
                    }
                    rr = obtainRequest(142, result, this.mRILDefaultWorkSource);
                    stringBuilder = new StringBuilder();
                    stringBuilder.append(rr.serialString());
                    stringBuilder.append("> ");
                    stringBuilder.append(requestToString(rr.mRequest));
                    riljLog(stringBuilder.toString());
                    try {
                        radioProxy11.startNetworkScan(rr.mSerial, request2);
                    } catch (RemoteException | RuntimeException e2) {
                        handleRadioProxyExceptionForRR(rr, "startNetworkScan", e2);
                    }
                } else if (result != null) {
                    AsyncResult.forMessage(result, null, CommandException.fromRilErrno(6));
                    result.sendToTarget();
                }
            }
        }
    }

    public void stopNetworkScan(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            android.hardware.radio.V1_1.IRadio radioProxy11 = android.hardware.radio.V1_1.IRadio.castFrom(radioProxy);
            if (radioProxy11 != null) {
                RILRequest rr = obtainRequest(143, result, this.mRILDefaultWorkSource);
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append(rr.serialString());
                stringBuilder.append("> ");
                stringBuilder.append(requestToString(rr.mRequest));
                riljLog(stringBuilder.toString());
                try {
                    radioProxy11.stopNetworkScan(rr.mSerial);
                } catch (RemoteException | RuntimeException e) {
                    handleRadioProxyExceptionForRR(rr, "stopNetworkScan", e);
                }
            } else if (result != null) {
                AsyncResult.forMessage(result, null, CommandException.fromRilErrno(6));
                result.sendToTarget();
            }
        }
    }

    public void startDtmf(char c, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(49, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                int i = rr.mSerial;
                StringBuilder stringBuilder2 = new StringBuilder();
                stringBuilder2.append(c);
                stringBuilder2.append("");
                radioProxy.startDtmf(i, stringBuilder2.toString());
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "startDtmf", e);
            }
        }
    }

    public void stopDtmf(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(50, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.stopDtmf(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "stopDtmf", e);
            }
        }
    }

    public void separateConnection(int gsmIndex, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(52, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" gsmIndex = ");
            stringBuilder.append(gsmIndex);
            riljLog(stringBuilder.toString());
            try {
                radioProxy.separateConnection(rr.mSerial, gsmIndex);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "separateConnection", e);
            }
        }
    }

    public void getBasebandVersion(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(51, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.getBasebandVersion(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "getBasebandVersion", e);
            }
        }
    }

    public void setMute(boolean enableMute, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(53, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" enableMute = ");
            stringBuilder.append(enableMute);
            riljLog(stringBuilder.toString());
            try {
                radioProxy.setMute(rr.mSerial, enableMute);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "setMute", e);
            }
        }
    }

    public void getMute(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(54, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.getMute(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "getMute", e);
            }
        }
    }

    public void queryCLIP(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(55, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.getClip(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "queryCLIP", e);
            }
        }
    }

    @Deprecated
    public void getPDPContextList(Message result) {
        getDataCallList(result);
    }

    public void getDataCallList(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(57, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.getDataCallList(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "getDataCallList", e);
            }
        }
    }

    public void invokeOemRilRequestRaw(byte[] data, Message response) {
        vendor.huawei.hardware.radio.deprecated.V1_0.IOemHook oemHookProxy = getHwOemHookProxy(response);
        if (oemHookProxy != null) {
            RILRequest rr = obtainRequest(59, response, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append("[");
            stringBuilder.append(IccUtils.bytesToHexString(data));
            stringBuilder.append("]");
            riljLog(stringBuilder.toString());
            try {
                oemHookProxy.sendRequestRaw(rr.mSerial, primitiveArrayToArrayList(data));
                return;
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "invokeOemRilRequestRaw", e);
                return;
            }
        }
        riljLog("Radio Oem Hook Service is disabled for P and later devices. ");
    }

    public void invokeOemRilRequestStrings(String[] strings, Message result) {
        vendor.huawei.hardware.radio.deprecated.V1_0.IOemHook oemHookProxy = getHwOemHookProxy(result);
        if (oemHookProxy != null) {
            RILRequest rr = obtainRequest(60, result, this.mRILDefaultWorkSource);
            String logStr = "";
            for (String append : strings) {
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append(logStr);
                stringBuilder.append(append);
                stringBuilder.append(" ");
                logStr = stringBuilder.toString();
            }
            StringBuilder stringBuilder2 = new StringBuilder();
            stringBuilder2.append(rr.serialString());
            stringBuilder2.append("> ");
            stringBuilder2.append(requestToString(rr.mRequest));
            stringBuilder2.append(" strings = ");
            stringBuilder2.append(logStr);
            riljLog(stringBuilder2.toString());
            try {
                oemHookProxy.sendRequestStrings(rr.mSerial, new ArrayList(Arrays.asList(strings)));
                return;
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "invokeOemRilRequestStrings", e);
                return;
            }
        }
        riljLog("Radio Oem Hook Service is disabled for P and later devices. ");
    }

    public void setSuppServiceNotifications(boolean enable, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(62, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" enable = ");
            stringBuilder.append(enable);
            riljLog(stringBuilder.toString());
            try {
                radioProxy.setSuppServiceNotifications(rr.mSerial, enable);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "setSuppServiceNotifications", e);
            }
        }
    }

    public void writeSmsToSim(int status, String smsc, String pdu, Message result) {
        status = translateStatus(status);
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(63, result, this.mRILDefaultWorkSource);
            SmsWriteArgs args = new SmsWriteArgs();
            args.status = status;
            args.smsc = convertNullToEmptyString(smsc);
            args.pdu = convertNullToEmptyString(pdu);
            try {
                radioProxy.writeSmsToSim(rr.mSerial, args);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "writeSmsToSim", e);
            }
        }
    }

    public void deleteSmsOnSim(int index, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(64, result, this.mRILDefaultWorkSource);
            try {
                radioProxy.deleteSmsOnSim(rr.mSerial, index);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "deleteSmsOnSim", e);
            }
        }
    }

    public void setBandMode(int bandMode, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(65, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" bandMode = ");
            stringBuilder.append(bandMode);
            riljLog(stringBuilder.toString());
            try {
                radioProxy.setBandMode(rr.mSerial, bandMode);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "setBandMode", e);
            }
        }
    }

    public void queryAvailableBandMode(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(66, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.getAvailableBandModes(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "queryAvailableBandMode", e);
            }
        }
    }

    public void sendEnvelope(String contents, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(69, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" contents = ");
            stringBuilder.append(contents);
            riljLog(stringBuilder.toString());
            try {
                radioProxy.sendEnvelope(rr.mSerial, convertNullToEmptyString(contents));
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "sendEnvelope", e);
            }
        }
    }

    public void sendTerminalResponse(String contents, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(70, result, this.mRILDefaultWorkSource);
            if (Log.HWINFO) {
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append(rr.serialString());
                stringBuilder.append("> ");
                stringBuilder.append(requestToString(rr.mRequest));
                stringBuilder.append(" contents = ");
                stringBuilder.append(Build.IS_DEBUGGABLE ? contents : censoredTerminalResponse(contents));
                riljLog(stringBuilder.toString());
            }
            try {
                radioProxy.sendTerminalResponseToSim(rr.mSerial, convertNullToEmptyString(contents));
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "sendTerminalResponse", e);
            }
        }
    }

    private String censoredTerminalResponse(String terminalResponse) {
        try {
            byte[] bytes = IccUtils.hexStringToBytes(terminalResponse);
            if (bytes == null) {
                return terminalResponse;
            }
            int from = 0;
            for (ComprehensionTlv ctlv : ComprehensionTlv.decodeMany(bytes, null)) {
                if (ComprehensionTlvTag.TEXT_STRING.value() == ctlv.getTag()) {
                    terminalResponse = terminalResponse.toLowerCase().replace(IccUtils.bytesToHexString(Arrays.copyOfRange(ctlv.getRawValue(), from, ctlv.getValueIndex() + ctlv.getLength())), "********");
                }
                from = ctlv.getValueIndex() + ctlv.getLength();
            }
            return terminalResponse;
        } catch (Exception e) {
            String str = RILJ_LOG_TAG;
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("Could not censor the terminal response: ");
            stringBuilder.append(e);
            Rlog.e(str, stringBuilder.toString());
            return null;
        }
    }

    public void sendEnvelopeWithStatus(String contents, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(107, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" contents = ");
            stringBuilder.append(contents);
            riljLog(stringBuilder.toString());
            try {
                radioProxy.sendEnvelopeWithStatus(rr.mSerial, convertNullToEmptyString(contents));
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "sendEnvelopeWithStatus", e);
            }
        }
    }

    public void explicitCallTransfer(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(72, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.explicitCallTransfer(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "explicitCallTransfer", e);
            }
        }
    }

    public void setPreferredNetworkType(int networkType, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(73, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" networkType = ");
            stringBuilder.append(networkType);
            riljLog(stringBuilder.toString());
            this.mPreferredNetworkType = networkType;
            custSetModemProperties();
            this.mMetrics.writeSetPreferredNetworkType(this.mPhoneId.intValue(), networkType);
            try {
                radioProxy.setPreferredNetworkType(rr.mSerial, networkType);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "setPreferredNetworkType", e);
            }
        }
    }

    public void getPreferredNetworkType(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(74, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.getPreferredNetworkType(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "getPreferredNetworkType", e);
            }
        }
    }

    public void getNeighboringCids(Message result, WorkSource workSource) {
        workSource = getDeafultWorkSourceIfInvalid(workSource);
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(75, result, workSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.getNeighboringCids(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "getNeighboringCids", e);
            }
        }
    }

    public void setLocationUpdates(boolean enable, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(76, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" enable = ");
            stringBuilder.append(enable);
            riljLog(stringBuilder.toString());
            try {
                radioProxy.setLocationUpdates(rr.mSerial, enable);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "setLocationUpdates", e);
            }
        }
    }

    public void setCdmaSubscriptionSource(int cdmaSubscription, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(77, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" cdmaSubscription = ");
            stringBuilder.append(cdmaSubscription);
            riljLog(stringBuilder.toString());
            try {
                radioProxy.setCdmaSubscriptionSource(rr.mSerial, cdmaSubscription);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "setCdmaSubscriptionSource", e);
            }
        }
    }

    public void queryCdmaRoamingPreference(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(79, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.getCdmaRoamingPreference(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "queryCdmaRoamingPreference", e);
            }
        }
    }

    public void setCdmaRoamingPreference(int cdmaRoamingType, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(78, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" cdmaRoamingType = ");
            stringBuilder.append(cdmaRoamingType);
            riljLog(stringBuilder.toString());
            try {
                radioProxy.setCdmaRoamingPreference(rr.mSerial, cdmaRoamingType);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "setCdmaRoamingPreference", e);
            }
        }
    }

    public void queryTTYMode(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(81, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.getTTYMode(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "queryTTYMode", e);
            }
        }
    }

    public void setTTYMode(int ttyMode, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(80, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" ttyMode = ");
            stringBuilder.append(ttyMode);
            riljLog(stringBuilder.toString());
            try {
                radioProxy.setTTYMode(rr.mSerial, ttyMode);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "setTTYMode", e);
            }
        }
    }

    public void setPreferredVoicePrivacy(boolean enable, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(82, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" enable = ");
            stringBuilder.append(enable);
            riljLog(stringBuilder.toString());
            try {
                radioProxy.setPreferredVoicePrivacy(rr.mSerial, enable);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "setPreferredVoicePrivacy", e);
            }
        }
    }

    public void getPreferredVoicePrivacy(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(83, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.getPreferredVoicePrivacy(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "getPreferredVoicePrivacy", e);
            }
        }
    }

    public void sendCDMAFeatureCode(String featureCode, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(84, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder;
            if (featureCode != null) {
                stringBuilder = new StringBuilder();
                stringBuilder.append(rr.serialString());
                stringBuilder.append("> ");
                stringBuilder.append(requestToString(rr.mRequest));
                stringBuilder.append(" : ");
                stringBuilder.append(featureCode.replaceAll("\\d{4}$", "****"));
                riljLog(stringBuilder.toString());
            } else {
                stringBuilder = new StringBuilder();
                stringBuilder.append(rr.serialString());
                stringBuilder.append("> ");
                stringBuilder.append(requestToString(rr.mRequest));
                riljLog(stringBuilder.toString());
            }
            try {
                radioProxy.sendCDMAFeatureCode(rr.mSerial, convertNullToEmptyString(featureCode));
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "sendCDMAFeatureCode", e);
            }
        }
    }

    public void sendBurstDtmf(String dtmfString, int on, int off, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(85, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" dtmfString = * on = ");
            stringBuilder.append(on);
            stringBuilder.append(" off = ");
            stringBuilder.append(off);
            riljLog(stringBuilder.toString());
            try {
                radioProxy.sendBurstDtmf(rr.mSerial, convertNullToEmptyString(dtmfString), on, off);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "sendBurstDtmf", e);
            }
        }
    }

    private void constructCdmaSendSmsRilRequest(CdmaSmsMessage msg, byte[] pdu) {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(pdu));
        try {
            int i;
            msg.teleserviceId = dis.readInt();
            int i2 = 0;
            boolean z = true;
            msg.isServicePresent = ((byte) dis.readInt()) == (byte) 1;
            msg.serviceCategory = dis.readInt();
            msg.address.digitMode = dis.read();
            msg.address.numberMode = dis.read();
            msg.address.numberType = dis.read();
            msg.address.numberPlan = dis.read();
            int addrNbrOfDigits = (byte) dis.read();
            for (i = 0; i < addrNbrOfDigits; i++) {
                msg.address.digits.add(Byte.valueOf(dis.readByte()));
            }
            msg.subAddress.subaddressType = dis.read();
            CdmaSmsSubaddress cdmaSmsSubaddress = msg.subAddress;
            if (((byte) dis.read()) != (byte) 1) {
                z = false;
            }
            cdmaSmsSubaddress.odd = z;
            int subaddrNbrOfDigits = (byte) dis.read();
            for (i = 0; i < subaddrNbrOfDigits; i++) {
                msg.subAddress.digits.add(Byte.valueOf(dis.readByte()));
            }
            i = dis.read();
            while (i2 < i) {
                msg.bearerData.add(Byte.valueOf(dis.readByte()));
                i2++;
            }
        } catch (IOException ex) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("sendSmsCdma: conversion from input stream to object failed: ");
            stringBuilder.append(ex);
            riljLog(stringBuilder.toString());
        }
    }

    public void sendCdmaSms(byte[] pdu, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(87, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            CdmaSmsMessage msg = new CdmaSmsMessage();
            constructCdmaSendSmsRilRequest(msg, pdu);
            try {
                radioProxy.sendCdmaSms(rr.mSerial, msg);
                this.mMetrics.writeRilSendSms(this.mPhoneId.intValue(), rr.mSerial, 2, 2);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "sendCdmaSms", e);
            }
        }
    }

    public void acknowledgeLastIncomingCdmaSms(boolean success, int cause, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(88, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" success = ");
            stringBuilder.append(success);
            stringBuilder.append(" cause = ");
            stringBuilder.append(cause);
            riljLog(stringBuilder.toString());
            CdmaSmsAck msg = new CdmaSmsAck();
            msg.errorClass = success ^ 1;
            msg.smsCauseCode = cause;
            try {
                radioProxy.acknowledgeLastIncomingCdmaSms(rr.mSerial, msg);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "acknowledgeLastIncomingCdmaSms", e);
            }
        }
    }

    public void getGsmBroadcastConfig(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(89, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.getGsmBroadcastConfig(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "getGsmBroadcastConfig", e);
            }
        }
    }

    public void setGsmBroadcastConfig(SmsBroadcastConfigInfo[] config, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(90, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" with ");
            stringBuilder.append(config.length);
            stringBuilder.append(" configs : ");
            riljLog(stringBuilder.toString());
            int i = 0;
            for (SmsBroadcastConfigInfo smsBroadcastConfigInfo : config) {
                riljLog(smsBroadcastConfigInfo.toString());
            }
            ArrayList<GsmBroadcastSmsConfigInfo> configs = new ArrayList();
            int numOfConfig = config.length;
            while (i < numOfConfig) {
                GsmBroadcastSmsConfigInfo info = new GsmBroadcastSmsConfigInfo();
                info.fromServiceId = config[i].getFromServiceId();
                info.toServiceId = config[i].getToServiceId();
                info.fromCodeScheme = config[i].getFromCodeScheme();
                info.toCodeScheme = config[i].getToCodeScheme();
                info.selected = config[i].isSelected();
                configs.add(info);
                i++;
            }
            try {
                radioProxy.setGsmBroadcastConfig(rr.mSerial, configs);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "setGsmBroadcastConfig", e);
            }
        }
    }

    public void setGsmBroadcastActivation(boolean activate, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(91, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" activate = ");
            stringBuilder.append(activate);
            riljLog(stringBuilder.toString());
            try {
                radioProxy.setGsmBroadcastActivation(rr.mSerial, activate);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "setGsmBroadcastActivation", e);
            }
        }
    }

    public void getCdmaBroadcastConfig(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(92, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.getCdmaBroadcastConfig(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "getCdmaBroadcastConfig", e);
            }
        }
    }

    public void setCdmaBroadcastConfig(CdmaSmsBroadcastConfigInfo[] configs, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(93, result, this.mRILDefaultWorkSource);
            ArrayList<CdmaBroadcastSmsConfigInfo> halConfigs = new ArrayList();
            for (CdmaSmsBroadcastConfigInfo config : configs) {
                for (int i = config.getFromServiceCategory(); i <= config.getToServiceCategory(); i++) {
                    CdmaBroadcastSmsConfigInfo info = new CdmaBroadcastSmsConfigInfo();
                    info.serviceCategory = i;
                    info.language = config.getLanguage();
                    info.selected = config.isSelected();
                    halConfigs.add(info);
                }
            }
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" with ");
            stringBuilder.append(halConfigs.size());
            stringBuilder.append(" configs : ");
            riljLog(stringBuilder.toString());
            Iterator it = halConfigs.iterator();
            while (it.hasNext()) {
                riljLog(((CdmaBroadcastSmsConfigInfo) it.next()).toString());
            }
            try {
                radioProxy.setCdmaBroadcastConfig(rr.mSerial, halConfigs);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "setCdmaBroadcastConfig", e);
            }
        }
    }

    public void setCdmaBroadcastActivation(boolean activate, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(94, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" activate = ");
            stringBuilder.append(activate);
            riljLog(stringBuilder.toString());
            try {
                radioProxy.setCdmaBroadcastActivation(rr.mSerial, activate);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "setCdmaBroadcastActivation", e);
            }
        }
    }

    public void getCDMASubscription(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(95, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.getCDMASubscription(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "getCDMASubscription", e);
            }
        }
    }

    public void writeSmsToRuim(int status, String pdu, Message result) {
        status = translateStatus(status);
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(96, result, this.mRILDefaultWorkSource);
            CdmaSmsWriteArgs args = new CdmaSmsWriteArgs();
            args.status = status;
            writeContent(args.message, pdu);
            try {
                radioProxy.writeSmsToRuim(rr.mSerial, args);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "writeSmsToRuim", e);
            }
        }
    }

    public void deleteSmsOnRuim(int index, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(97, result, this.mRILDefaultWorkSource);
            try {
                radioProxy.deleteSmsOnRuim(rr.mSerial, index);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "deleteSmsOnRuim", e);
            }
        }
    }

    public void getDeviceIdentity(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(98, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.getDeviceIdentity(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "getDeviceIdentity", e);
            }
        }
    }

    public void exitEmergencyCallbackMode(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(99, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.exitEmergencyCallbackMode(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "exitEmergencyCallbackMode", e);
            }
        }
    }

    public void getSmscAddress(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(100, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.getSmscAddress(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "getSmscAddress", e);
            }
        }
    }

    public void setSmscAddress(String address, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(101, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" address = ");
            stringBuilder.append(address);
            riljLog(stringBuilder.toString());
            try {
                radioProxy.setSmscAddress(rr.mSerial, convertNullToEmptyString(address));
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "setSmscAddress", e);
            }
        }
    }

    public void reportSmsMemoryStatus(boolean available, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(102, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" available = ");
            stringBuilder.append(available);
            riljLog(stringBuilder.toString());
            try {
                radioProxy.reportSmsMemoryStatus(rr.mSerial, available);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "reportSmsMemoryStatus", e);
            }
        }
    }

    public void reportStkServiceIsRunning(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(103, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.reportStkServiceIsRunning(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "reportStkServiceIsRunning", e);
            }
        }
    }

    public void getCdmaSubscriptionSource(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(AbstractPhoneBase.EVENT_ECC_NUM, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.getCdmaSubscriptionSource(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "getCdmaSubscriptionSource", e);
            }
        }
    }

    public void acknowledgeIncomingGsmSmsWithPdu(boolean success, String ackPdu, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(106, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" success = ");
            stringBuilder.append(success);
            riljLog(stringBuilder.toString());
            try {
                radioProxy.acknowledgeIncomingGsmSmsWithPdu(rr.mSerial, success, convertNullToEmptyString(ackPdu));
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "acknowledgeIncomingGsmSmsWithPdu", e);
            }
        }
    }

    public void getVoiceRadioTechnology(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(AbstractPhoneBase.EVENT_GET_LTE_RELEASE_VERSION_DONE, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.getVoiceRadioTechnology(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "getVoiceRadioTechnology", e);
            }
        }
    }

    public void getCellInfoList(Message result, WorkSource workSource) {
        workSource = getDeafultWorkSourceIfInvalid(workSource);
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(109, result, workSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.getCellInfoList(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "getCellInfoList", e);
            }
        }
    }

    public void setCellInfoListRate(int rateInMillis, Message result, WorkSource workSource) {
        workSource = getDeafultWorkSourceIfInvalid(workSource);
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(110, result, workSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" rateInMillis = ");
            stringBuilder.append(rateInMillis);
            riljLog(stringBuilder.toString());
            try {
                radioProxy.setCellInfoListRate(rr.mSerial, rateInMillis);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "setCellInfoListRate", e);
            }
        }
    }

    void setCellInfoListRate() {
        setCellInfoListRate(KeepaliveStatus.INVALID_HANDLE, null, this.mRILDefaultWorkSource);
    }

    public void setInitialAttachApn(DataProfile dataProfile, boolean isRoaming, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(111, result, this.mRILDefaultWorkSource);
            DataProfile dp = dataProfile;
            try {
                Map<String, String> map = correctApnAuth(dp.getUserName(), dp.getAuthType(), dp.getPassword());
                dp.setUserName((String) map.get("userName"));
                dp.setPassword((String) map.get("password"));
                dp.setAuthType(Integer.parseInt((String) map.get("authType")));
            } catch (Exception e) {
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append(e);
                stringBuilder.append("The authType is not number");
                riljLog(stringBuilder.toString());
            }
            StringBuilder stringBuilder2 = new StringBuilder();
            stringBuilder2.append(rr.serialString());
            stringBuilder2.append("> ");
            stringBuilder2.append(requestToString(rr.mRequest));
            stringBuilder2.append(dp);
            riljLog(stringBuilder2.toString());
            stringBuilder2 = new StringBuilder();
            stringBuilder2.append(rr.serialString());
            stringBuilder2.append("> ");
            stringBuilder2.append(requestToString(rr.mRequest));
            stringBuilder2.append(dataProfile);
            riljLog(stringBuilder2.toString());
            try {
                radioProxy.setInitialAttachApn(rr.mSerial, convertToHalDataProfile(dataProfile), dataProfile.isModemCognitive(), isRoaming);
            } catch (RemoteException | RuntimeException e2) {
                handleRadioProxyExceptionForRR(rr, "setInitialAttachApn", e2);
            }
        }
    }

    public void getImsRegistrationState(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(112, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.getImsRegistrationState(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "getImsRegistrationState", e);
            }
        }
    }

    public void sendImsGsmSms(String smscPdu, String pdu, int retry, int messageRef, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(113, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            ImsSmsMessage msg = new ImsSmsMessage();
            msg.tech = 1;
            msg.retry = ((byte) retry) >= (byte) 1;
            msg.messageRef = messageRef;
            msg.gsmMessage.add(constructGsmSendSmsRilRequest(smscPdu, pdu));
            try {
                radioProxy.sendImsSms(rr.mSerial, msg);
                this.mMetrics.writeRilSendSms(this.mPhoneId.intValue(), rr.mSerial, 3, 1);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "sendImsGsmSms", e);
            }
        }
    }

    public void sendImsCdmaSms(byte[] pdu, int retry, int messageRef, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(113, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            ImsSmsMessage msg = new ImsSmsMessage();
            msg.tech = 2;
            boolean z = true;
            if (((byte) retry) < (byte) 1) {
                z = false;
            }
            msg.retry = z;
            msg.messageRef = messageRef;
            CdmaSmsMessage cdmaMsg = new CdmaSmsMessage();
            constructCdmaSendSmsRilRequest(cdmaMsg, pdu);
            msg.cdmaMessage.add(cdmaMsg);
            try {
                radioProxy.sendImsSms(rr.mSerial, msg);
                this.mMetrics.writeRilSendSms(this.mPhoneId.intValue(), rr.mSerial, 3, 2);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "sendImsCdmaSms", e);
            }
        }
    }

    private SimApdu createSimApdu(int channel, int cla, int instruction, int p1, int p2, int p3, String data) {
        SimApdu msg = new SimApdu();
        msg.sessionId = channel;
        msg.cla = cla;
        msg.instruction = instruction;
        msg.p1 = p1;
        msg.p2 = p2;
        msg.p3 = p3;
        msg.data = convertNullToEmptyString(data);
        return msg;
    }

    public void iccTransmitApduBasicChannel(int cla, int instruction, int p1, int p2, int p3, String data, Message result) {
        Message message = result;
        IRadio radioProxy = getRadioProxy(message);
        int i;
        int i2;
        int i3;
        if (radioProxy != null) {
            int i4;
            RILRequest rr = obtainRequest(114, message, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder;
            if (Build.IS_DEBUGGABLE) {
                stringBuilder = new StringBuilder();
                stringBuilder.append(rr.serialString());
                stringBuilder.append("> ");
                stringBuilder.append(requestToString(rr.mRequest));
                stringBuilder.append(" cla = ");
                i = cla;
                stringBuilder.append(i);
                stringBuilder.append(" instruction = ");
                i2 = instruction;
                stringBuilder.append(i2);
                stringBuilder.append(" p1 = ");
                i3 = p1;
                stringBuilder.append(i3);
                stringBuilder.append(" p2 =  p3 = ");
                i4 = p3;
                stringBuilder.append(i4);
                stringBuilder.append(" data = ");
                stringBuilder.append(data);
                riljLog(stringBuilder.toString());
            } else {
                i = cla;
                i2 = instruction;
                i3 = p1;
                i4 = p3;
                String str = data;
                stringBuilder = new StringBuilder();
                stringBuilder.append(rr.serialString());
                stringBuilder.append("> ");
                stringBuilder.append(requestToString(rr.mRequest));
                riljLog(stringBuilder.toString());
            }
            try {
                radioProxy.iccTransmitApduBasicChannel(rr.mSerial, createSimApdu(0, i, i2, i3, p2, i4, data));
                return;
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "iccTransmitApduBasicChannel", e);
                return;
            }
        }
        i = cla;
        i2 = instruction;
        i3 = p1;
    }

    public void iccOpenLogicalChannel(String aid, int p2, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(115, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder;
            if (Build.IS_DEBUGGABLE) {
                stringBuilder = new StringBuilder();
                stringBuilder.append(rr.serialString());
                stringBuilder.append("> ");
                stringBuilder.append(requestToString(rr.mRequest));
                stringBuilder.append(" aid = ");
                stringBuilder.append(aid);
                stringBuilder.append(" p2 = ");
                stringBuilder.append(p2);
                riljLog(stringBuilder.toString());
            } else {
                stringBuilder = new StringBuilder();
                stringBuilder.append(rr.serialString());
                stringBuilder.append("> ");
                stringBuilder.append(requestToString(rr.mRequest));
                riljLog(stringBuilder.toString());
            }
            try {
                radioProxy.iccOpenLogicalChannel(rr.mSerial, convertNullToEmptyString(aid), p2);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "iccOpenLogicalChannel", e);
            }
        }
    }

    public void iccCloseLogicalChannel(int channel, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(116, result, this.mRILDefaultWorkSource);
            if (Log.HWINFO) {
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append(rr.serialString());
                stringBuilder.append("> ");
                stringBuilder.append(requestToString(rr.mRequest));
                stringBuilder.append(" channel = ");
                stringBuilder.append(channel);
                riljLog(stringBuilder.toString());
            }
            try {
                radioProxy.iccCloseLogicalChannel(rr.mSerial, channel);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "iccCloseLogicalChannel", e);
            }
        }
    }

    public void iccTransmitApduLogicalChannel(int channel, int cla, int instruction, int p1, int p2, int p3, String data, Message result) {
        if (channel <= 0) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("Invalid channel in iccTransmitApduLogicalChannel: ");
            stringBuilder.append(channel);
            riljLoge(stringBuilder.toString());
            if (result != null) {
                AsyncResult.forMessage(result, null, new CommandException(Error.INVALID_PARAMETER));
                result.sendToTarget();
            }
            return;
        }
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(117, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder2;
            if (Build.IS_DEBUGGABLE) {
                stringBuilder2 = new StringBuilder();
                stringBuilder2.append(rr.serialString());
                stringBuilder2.append("> ");
                stringBuilder2.append(requestToString(rr.mRequest));
                stringBuilder2.append(" channel = ");
                stringBuilder2.append(channel);
                stringBuilder2.append(" cla = ");
                stringBuilder2.append(cla);
                stringBuilder2.append(" instruction = ");
                stringBuilder2.append(instruction);
                stringBuilder2.append(" p1 = ");
                stringBuilder2.append(p1);
                stringBuilder2.append(" p2 =  p3 = ");
                stringBuilder2.append(p3);
                stringBuilder2.append(" data = ");
                stringBuilder2.append(data);
                riljLog(stringBuilder2.toString());
            } else {
                stringBuilder2 = new StringBuilder();
                stringBuilder2.append(rr.serialString());
                stringBuilder2.append("> ");
                stringBuilder2.append(requestToString(rr.mRequest));
                riljLog(stringBuilder2.toString());
            }
            try {
                radioProxy.iccTransmitApduLogicalChannel(rr.mSerial, createSimApdu(channel, cla, instruction, p1, p2, p3, data));
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "iccTransmitApduLogicalChannel", e);
            }
        }
    }

    public void nvReadItem(int itemID, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(118, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" itemId = ");
            stringBuilder.append(itemID);
            riljLog(stringBuilder.toString());
            try {
                radioProxy.nvReadItem(rr.mSerial, itemID);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "nvReadItem", e);
            }
        }
    }

    public void nvWriteItem(int itemId, String itemValue, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(119, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" itemId = ");
            stringBuilder.append(itemId);
            stringBuilder.append(" itemValue = ");
            stringBuilder.append(itemValue);
            riljLog(stringBuilder.toString());
            NvWriteItem item = new NvWriteItem();
            item.itemId = itemId;
            item.value = convertNullToEmptyString(itemValue);
            try {
                radioProxy.nvWriteItem(rr.mSerial, item);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "nvWriteItem", e);
            }
        }
    }

    public void nvWriteCdmaPrl(byte[] preferredRoamingList, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(120, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" PreferredRoamingList = 0x");
            stringBuilder.append(IccUtils.bytesToHexString(preferredRoamingList));
            riljLog(stringBuilder.toString());
            ArrayList<Byte> arrList = new ArrayList();
            for (byte valueOf : preferredRoamingList) {
                arrList.add(Byte.valueOf(valueOf));
            }
            try {
                radioProxy.nvWriteCdmaPrl(rr.mSerial, arrList);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "nvWriteCdmaPrl", e);
            }
        }
    }

    public void nvResetConfig(int resetType, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(121, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" resetType = ");
            stringBuilder.append(resetType);
            riljLog(stringBuilder.toString());
            try {
                radioProxy.nvResetConfig(rr.mSerial, convertToHalResetNvType(resetType));
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "nvResetConfig", e);
            }
        }
    }

    public void setUiccSubscription(int slotId, int appIndex, int subId, int subStatus, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(122, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" slot = ");
            stringBuilder.append(slotId);
            stringBuilder.append(" appIndex = ");
            stringBuilder.append(appIndex);
            stringBuilder.append(" subId = ");
            stringBuilder.append(subId);
            stringBuilder.append(" subStatus = ");
            stringBuilder.append(subStatus);
            riljLog(stringBuilder.toString());
            SelectUiccSub info = new SelectUiccSub();
            info.slot = slotId;
            info.appIndex = appIndex;
            info.subType = subId;
            info.actStatus = subStatus;
            try {
                radioProxy.setUiccSubscription(rr.mSerial, info);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "setUiccSubscription", e);
            }
        }
    }

    public void setDataAllowed(boolean allowed, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(123, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" allowed = ");
            stringBuilder.append(allowed);
            riljLog(stringBuilder.toString());
            try {
                radioProxy.setDataAllowed(rr.mSerial, allowed);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "setDataAllowed", e);
            }
        }
    }

    public void getHardwareConfig(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(124, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.getHardwareConfig(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "getHardwareConfig", e);
            }
        }
    }

    public void requestIccSimAuthentication(int authContext, String data, String aid, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(125, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.requestIccSimAuthentication(rr.mSerial, authContext, convertNullToEmptyString(data), convertNullToEmptyString(aid));
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "requestIccSimAuthentication", e);
            }
        }
    }

    public void setDataProfile(DataProfile[] dps, boolean isRoaming, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(128, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" with data profiles : ");
            riljLog(stringBuilder.toString());
            int i = 0;
            for (DataProfile profile : dps) {
                riljLog(profile.toString());
            }
            ArrayList<DataProfileInfo> dpis = new ArrayList();
            int length = dps.length;
            while (i < length) {
                dpis.add(convertToHalDataProfile(dps[i]));
                i++;
            }
            try {
                radioProxy.setDataProfile(rr.mSerial, dpis, isRoaming);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "setDataProfile", e);
            }
        }
    }

    public void requestShutdown(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(129, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.requestShutdown(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "requestShutdown", e);
            }
        }
    }

    public void getRadioCapability(Message response) {
        IRadio radioProxy = getRadioProxy(response);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(130, response, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.getRadioCapability(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "getRadioCapability", e);
            }
        }
    }

    public void setRadioCapability(RadioCapability rc, Message response) {
        IRadio radioProxy = getRadioProxy(response);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(131, response, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" RadioCapability = ");
            stringBuilder.append(rc.toString());
            riljLog(stringBuilder.toString());
            RadioCapability halRc = new RadioCapability();
            halRc.session = rc.getSession();
            halRc.phase = rc.getPhase();
            halRc.raf = rc.getRadioAccessFamily();
            halRc.logicalModemUuid = convertNullToEmptyString(rc.getLogicalModemUuid());
            halRc.status = rc.getStatus();
            try {
                radioProxy.setRadioCapability(rr.mSerial, halRc);
            } catch (Exception e) {
                handleRadioProxyExceptionForRR(rr, "setRadioCapability", e);
            }
        }
    }

    public void startLceService(int reportIntervalMs, boolean pullMode, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (android.hardware.radio.V1_2.IRadio.castFrom(radioProxy) == null && radioProxy != null) {
            RILRequest rr = obtainRequest(132, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" reportIntervalMs = ");
            stringBuilder.append(reportIntervalMs);
            stringBuilder.append(" pullMode = ");
            stringBuilder.append(pullMode);
            riljLog(stringBuilder.toString());
            try {
                radioProxy.startLceService(rr.mSerial, reportIntervalMs, pullMode);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "startLceService", e);
            }
        }
    }

    public void stopLceService(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (android.hardware.radio.V1_2.IRadio.castFrom(radioProxy) == null && radioProxy != null) {
            RILRequest rr = obtainRequest(133, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.stopLceService(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "stopLceService", e);
            }
        }
    }

    @Deprecated
    public void pullLceData(Message response) {
        IRadio radioProxy = getRadioProxy(response);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(134, response, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.pullLceData(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "pullLceData", e);
            }
        }
    }

    public void getModemActivityInfo(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(135, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.getModemActivityInfo(rr.mSerial);
                Message msg = this.mRilHandler.obtainMessage(5);
                msg.obj = null;
                msg.arg1 = rr.mSerial;
                this.mRilHandler.sendMessageDelayed(msg, 2000);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "getModemActivityInfo", e);
            }
        }
    }

    public void setAllowedCarriers(List<CarrierIdentifier> carriers, Message result) {
        Preconditions.checkNotNull(carriers, "Allowed carriers list cannot be null.");
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(136, result, this.mRILDefaultWorkSource);
            boolean allAllowed = false;
            String logStr = "";
            for (int i = 0; i < carriers.size(); i++) {
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append(logStr);
                stringBuilder.append(carriers.get(i));
                stringBuilder.append(" ");
                logStr = stringBuilder.toString();
            }
            StringBuilder stringBuilder2 = new StringBuilder();
            stringBuilder2.append(rr.serialString());
            stringBuilder2.append("> ");
            stringBuilder2.append(requestToString(rr.mRequest));
            stringBuilder2.append(" carriers = ");
            stringBuilder2.append(logStr);
            riljLog(stringBuilder2.toString());
            if (carriers.size() == 0) {
                allAllowed = true;
            }
            boolean allAllowed2 = allAllowed;
            CarrierRestrictions carrierList = new CarrierRestrictions();
            for (CarrierIdentifier ci : carriers) {
                Carrier c = new Carrier();
                c.mcc = convertNullToEmptyString(ci.getMcc());
                c.mnc = convertNullToEmptyString(ci.getMnc());
                int matchType = 0;
                String matchData = null;
                if (!TextUtils.isEmpty(ci.getSpn())) {
                    matchType = 1;
                    matchData = ci.getSpn();
                } else if (!TextUtils.isEmpty(ci.getImsi())) {
                    matchType = 2;
                    matchData = ci.getImsi();
                } else if (!TextUtils.isEmpty(ci.getGid1())) {
                    matchType = 3;
                    matchData = ci.getGid1();
                } else if (!TextUtils.isEmpty(ci.getGid2())) {
                    matchType = 4;
                    matchData = ci.getGid2();
                }
                c.matchType = matchType;
                c.matchData = convertNullToEmptyString(matchData);
                carrierList.allowedCarriers.add(c);
            }
            try {
                radioProxy.setAllowedCarriers(rr.mSerial, allAllowed2, carrierList);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "setAllowedCarriers", e);
            }
        }
    }

    public void getAllowedCarriers(Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(137, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.getAllowedCarriers(rr.mSerial);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "getAllowedCarriers", e);
            }
        }
    }

    public void sendDeviceState(int stateType, boolean state, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(138, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" ");
            stringBuilder.append(stateType);
            stringBuilder.append(":");
            stringBuilder.append(state);
            riljLog(stringBuilder.toString());
            try {
                radioProxy.sendDeviceState(rr.mSerial, stateType, state);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "sendDeviceState", e);
            }
        }
    }

    public void setUnsolResponseFilter(int filter, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(139, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" ");
            stringBuilder.append(filter);
            riljLog(stringBuilder.toString());
            android.hardware.radio.V1_2.IRadio radioProxy12 = android.hardware.radio.V1_2.IRadio.castFrom(radioProxy);
            if (radioProxy12 != null) {
                try {
                    radioProxy12.setIndicationFilter_1_2(rr.mSerial, filter);
                    return;
                } catch (RemoteException | RuntimeException e) {
                    handleRadioProxyExceptionForRR(rr, "setIndicationFilter_1_2", e);
                    return;
                }
            }
            try {
                radioProxy.setIndicationFilter(rr.mSerial, filter & 7);
            } catch (RemoteException | RuntimeException e2) {
                handleRadioProxyExceptionForRR(rr, "setIndicationFilter", e2);
            }
        }
    }

    public void setSignalStrengthReportingCriteria(int hysteresisMs, int hysteresisDb, int[] thresholdsDbm, int ran, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            android.hardware.radio.V1_2.IRadio radioProxy12 = android.hardware.radio.V1_2.IRadio.castFrom(radioProxy);
            if (radioProxy12 == null) {
                riljLoge("setSignalStrengthReportingCriteria ignored. RadioProxy 1.2 is null!");
                return;
            }
            RILRequest rr = obtainRequest(148, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy12.setSignalStrengthReportingCriteria(rr.mSerial, hysteresisMs, hysteresisDb, primitiveArrayToArrayList(thresholdsDbm), convertRanToHalRan(ran));
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "setSignalStrengthReportingCriteria", e);
            }
        }
    }

    public void setLinkCapacityReportingCriteria(int hysteresisMs, int hysteresisDlKbps, int hysteresisUlKbps, int[] thresholdsDlKbps, int[] thresholdsUlKbps, int ran, Message result) {
        Message message = result;
        IRadio radioProxy = getRadioProxy(message);
        if (radioProxy != null) {
            android.hardware.radio.V1_2.IRadio radioProxy12 = android.hardware.radio.V1_2.IRadio.castFrom(radioProxy);
            if (radioProxy12 == null) {
                riljLoge("setLinkCapacityReportingCriteria ignored. RadioProxy 1.2 is null!");
                return;
            }
            RILRequest rr = obtainRequest(149, message, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy12.setLinkCapacityReportingCriteria(rr.mSerial, hysteresisMs, hysteresisDlKbps, hysteresisUlKbps, primitiveArrayToArrayList(thresholdsDlKbps), primitiveArrayToArrayList(thresholdsUlKbps), convertRanToHalRan(ran));
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "setLinkCapacityReportingCriteria", e);
            }
        }
    }

    private static int convertRanToHalRan(int radioAccessNetwork) {
        switch (radioAccessNetwork) {
            case 1:
                return 1;
            case 2:
                return 2;
            case 3:
                return 3;
            case 4:
                return 4;
            case 5:
                return 5;
            default:
                return 0;
        }
    }

    public void setSimCardPower(int state, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(140, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" ");
            stringBuilder.append(state);
            riljLog(stringBuilder.toString());
            android.hardware.radio.V1_1.IRadio radioProxy11 = android.hardware.radio.V1_1.IRadio.castFrom(radioProxy);
            if (radioProxy11 == null) {
                switch (state) {
                    case 0:
                        radioProxy.setSimCardPower(rr.mSerial, false);
                        return;
                    case 1:
                        radioProxy.setSimCardPower(rr.mSerial, true);
                        return;
                    default:
                        if (result != null) {
                            try {
                                AsyncResult.forMessage(result, null, CommandException.fromRilErrno(6));
                                result.sendToTarget();
                                return;
                            } catch (RemoteException | RuntimeException e) {
                                handleRadioProxyExceptionForRR(rr, "setSimCardPower", e);
                                return;
                            }
                        }
                        return;
                }
            }
            try {
                radioProxy11.setSimCardPower_1_1(rr.mSerial, state);
            } catch (RemoteException | RuntimeException e2) {
                handleRadioProxyExceptionForRR(rr, "setSimCardPower", e2);
            }
        }
    }

    public void setCarrierInfoForImsiEncryption(ImsiEncryptionInfo imsiEncryptionInfo, Message result) {
        Preconditions.checkNotNull(imsiEncryptionInfo, "ImsiEncryptionInfo cannot be null.");
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            android.hardware.radio.V1_1.IRadio radioProxy11 = android.hardware.radio.V1_1.IRadio.castFrom(radioProxy);
            if (radioProxy11 != null) {
                RILRequest rr = obtainRequest(141, result, this.mRILDefaultWorkSource);
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append(rr.serialString());
                stringBuilder.append("> ");
                stringBuilder.append(requestToString(rr.mRequest));
                riljLog(stringBuilder.toString());
                try {
                    android.hardware.radio.V1_1.ImsiEncryptionInfo halImsiInfo = new android.hardware.radio.V1_1.ImsiEncryptionInfo();
                    halImsiInfo.mnc = imsiEncryptionInfo.getMnc();
                    halImsiInfo.mcc = imsiEncryptionInfo.getMcc();
                    halImsiInfo.keyIdentifier = imsiEncryptionInfo.getKeyIdentifier();
                    if (imsiEncryptionInfo.getExpirationTime() != null) {
                        halImsiInfo.expirationTime = imsiEncryptionInfo.getExpirationTime().getTime();
                    }
                    for (byte b : imsiEncryptionInfo.getPublicKey().getEncoded()) {
                        halImsiInfo.carrierKey.add(new Byte(b));
                    }
                    radioProxy11.setCarrierInfoForImsiEncryption(rr.mSerial, halImsiInfo);
                } catch (RemoteException | RuntimeException e) {
                    handleRadioProxyExceptionForRR(rr, "setCarrierInfoForImsiEncryption", e);
                }
            } else if (result != null) {
                AsyncResult.forMessage(result, null, CommandException.fromRilErrno(6));
                result.sendToTarget();
            }
        }
    }

    public void startNattKeepalive(int contextId, KeepalivePacketData packetData, int intervalMillis, Message result) {
        Preconditions.checkNotNull(packetData, "KeepaliveRequest cannot be null.");
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy == null) {
            riljLoge("Radio Proxy object is null!");
            return;
        }
        android.hardware.radio.V1_1.IRadio radioProxy11 = android.hardware.radio.V1_1.IRadio.castFrom(radioProxy);
        if (radioProxy11 == null) {
            if (result != null) {
                AsyncResult.forMessage(result, null, CommandException.fromRilErrno(6));
                result.sendToTarget();
            }
            return;
        }
        RILRequest rr = obtainRequest(146, result, this.mRILDefaultWorkSource);
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(rr.serialString());
        stringBuilder.append("> ");
        stringBuilder.append(requestToString(rr.mRequest));
        riljLog(stringBuilder.toString());
        try {
            KeepaliveRequest req = new KeepaliveRequest();
            req.cid = contextId;
            if (packetData.dstAddress instanceof Inet4Address) {
                req.type = 0;
            } else if (packetData.dstAddress instanceof Inet6Address) {
                req.type = 1;
            } else {
                AsyncResult.forMessage(result, null, CommandException.fromRilErrno(44));
                result.sendToTarget();
                return;
            }
            appendPrimitiveArrayToArrayList(packetData.srcAddress.getAddress(), req.sourceAddress);
            req.sourcePort = packetData.srcPort;
            appendPrimitiveArrayToArrayList(packetData.dstAddress.getAddress(), req.destinationAddress);
            req.destinationPort = packetData.dstPort;
            radioProxy11.startKeepalive(rr.mSerial, req);
        } catch (RemoteException | RuntimeException e) {
            handleRadioProxyExceptionForRR(rr, "startNattKeepalive", e);
        }
    }

    public void stopNattKeepalive(int sessionHandle, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy == null) {
            Rlog.e(RILJ_LOG_TAG, "Radio Proxy object is null!");
            return;
        }
        android.hardware.radio.V1_1.IRadio radioProxy11 = android.hardware.radio.V1_1.IRadio.castFrom(radioProxy);
        if (radioProxy11 == null) {
            if (result != null) {
                AsyncResult.forMessage(result, null, CommandException.fromRilErrno(6));
                result.sendToTarget();
            }
            return;
        }
        RILRequest rr = obtainRequest(147, result, this.mRILDefaultWorkSource);
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(rr.serialString());
        stringBuilder.append("> ");
        stringBuilder.append(requestToString(rr.mRequest));
        riljLog(stringBuilder.toString());
        try {
            radioProxy11.stopKeepalive(rr.mSerial, sessionHandle);
        } catch (RemoteException | RuntimeException e) {
            handleRadioProxyExceptionForRR(rr, "stopNattKeepalive", e);
        }
    }

    public void getIMEI(Message result) {
        throw new RuntimeException("getIMEI not expected to be called");
    }

    public void getIMEISV(Message result) {
        throw new RuntimeException("getIMEISV not expected to be called");
    }

    @Deprecated
    public void getLastPdpFailCause(Message result) {
        throw new RuntimeException("getLastPdpFailCause not expected to be called");
    }

    public void getLastDataCallFailCause(Message result) {
        throw new RuntimeException("getLastDataCallFailCause not expected to be called");
    }

    private int translateStatus(int status) {
        int i = status & 7;
        if (i == 1) {
            return 1;
        }
        if (i == 3) {
            return 0;
        }
        if (i == 5) {
            return 3;
        }
        if (i != 7) {
            return 1;
        }
        return 2;
    }

    public void resetRadio(Message result) {
        throw new RuntimeException("resetRadio not expected to be called");
    }

    public void handleCallSetupRequestFromSim(boolean accept, Message result) {
        IRadio radioProxy = getRadioProxy(result);
        if (radioProxy != null) {
            RILRequest rr = obtainRequest(71, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
            try {
                radioProxy.handleStkCallSetupRequestFromSim(rr.mSerial, accept);
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "getAllowedCarriers", e);
            }
        }
    }

    void processIndication(int indicationType) {
        if (indicationType == 1) {
            sendAck();
            riljLog("Unsol response received; Sending ack to ril.cpp");
        }
    }

    void processRequestAck(int serial) {
        RILRequest rr;
        synchronized (this.mRequestList) {
            rr = (RILRequest) this.mRequestList.get(serial);
        }
        if (rr == null) {
            String str = RILJ_LOG_TAG;
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("processRequestAck: Unexpected solicited ack response! serial: ");
            stringBuilder.append(serial);
            Rlog.w(str, stringBuilder.toString());
            return;
        }
        decrementWakeLock(rr);
        StringBuilder stringBuilder2 = new StringBuilder();
        stringBuilder2.append(rr.serialString());
        stringBuilder2.append(" Ack < ");
        stringBuilder2.append(requestToString(rr.mRequest));
        riljLog(stringBuilder2.toString());
    }

    @VisibleForTesting
    public RILRequest processResponse(RadioResponseInfo responseInfo) {
        int serial = responseInfo.serial;
        int error = responseInfo.error;
        int type = responseInfo.type;
        RILRequest rr;
        String str;
        StringBuilder stringBuilder;
        StringBuilder stringBuilder2;
        if (type == 1) {
            synchronized (this.mRequestList) {
                rr = (RILRequest) this.mRequestList.get(serial);
            }
            if (rr == null) {
                str = RILJ_LOG_TAG;
                stringBuilder = new StringBuilder();
                stringBuilder.append("Unexpected solicited ack response! sn: ");
                stringBuilder.append(serial);
                Rlog.w(str, stringBuilder.toString());
            } else {
                decrementWakeLock(rr);
                stringBuilder2 = new StringBuilder();
                stringBuilder2.append(rr.serialString());
                stringBuilder2.append(" Ack < ");
                stringBuilder2.append(requestToString(rr.mRequest));
                riljLog(stringBuilder2.toString());
            }
            return rr;
        }
        rr = findAndRemoveRequestFromList(serial);
        if (rr == null) {
            str = RILJ_LOG_TAG;
            stringBuilder = new StringBuilder();
            stringBuilder.append("processResponse: Unexpected response! serial: ");
            stringBuilder.append(serial);
            stringBuilder.append(" error: ");
            stringBuilder.append(error);
            Rlog.e(str, stringBuilder.toString());
            return null;
        }
        addToRilHistogram(rr);
        if (type == 2) {
            sendAck();
            stringBuilder = new StringBuilder();
            stringBuilder.append("Response received for ");
            stringBuilder.append(rr.serialString());
            stringBuilder.append(" ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" Sending ack to ril.cpp");
            riljLog(stringBuilder.toString());
        }
        int i = rr.mRequest;
        if (i == 3 || i == 5) {
            if (this.mIccStatusChangedRegistrants != null) {
                stringBuilder = new StringBuilder();
                stringBuilder.append("ON enter sim puk fakeSimStatusChanged: reg count=");
                stringBuilder.append(this.mIccStatusChangedRegistrants.size());
                riljLog(stringBuilder.toString());
                this.mIccStatusChangedRegistrants.notifyRegistrants();
            }
        } else if (i == 129) {
            setRadioState(RadioState.RADIO_UNAVAILABLE);
        }
        if (error != 0) {
            i = rr.mRequest;
            if (!(i == 2 || i == 4 || i == 43)) {
                switch (i) {
                    case 6:
                    case 7:
                        break;
                }
            }
            if (this.mIccStatusChangedRegistrants != null) {
                stringBuilder2 = new StringBuilder();
                stringBuilder2.append("ON some errors fakeSimStatusChanged: reg count=");
                stringBuilder2.append(this.mIccStatusChangedRegistrants.size());
                riljLog(stringBuilder2.toString());
                this.mIccStatusChangedRegistrants.notifyRegistrants();
            }
        } else if (rr.mRequest == 14 && this.mTestingEmergencyCall.getAndSet(false) && this.mEmergencyCallbackModeRegistrant != null) {
            riljLog("testing emergency call, notify ECM Registrants");
            this.mEmergencyCallbackModeRegistrant.notifyRegistrant();
        }
        return rr;
    }

    @VisibleForTesting
    public void processResponseDone(RILRequest rr, RadioResponseInfo responseInfo, Object ret) {
        StringBuilder stringBuilder;
        if (responseInfo.error == 0) {
            stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("< ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" ");
            stringBuilder.append(retToString(rr.mRequest, ret));
            riljLog(stringBuilder.toString());
        } else {
            stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("< ");
            stringBuilder.append(requestToString(rr.mRequest));
            stringBuilder.append(" error ");
            stringBuilder.append(responseInfo.error);
            riljLog(stringBuilder.toString());
            rr.onError(responseInfo.error, ret);
        }
        this.mMetrics.writeOnRilSolicitedResponse(this.mPhoneId.intValue(), rr.mSerial, responseInfo.error, rr.mRequest, ret);
        if (rr != null) {
            if (responseInfo.type == 0) {
                decrementWakeLock(rr);
            }
            rr.release();
        }
    }

    private void sendAck() {
        RILRequest rr = RILRequest.obtain(800, null, this.mRILDefaultWorkSource);
        acquireWakeLock(rr, 1);
        IRadio radioProxy = getRadioProxy(null);
        if (radioProxy != null) {
            try {
                radioProxy.responseAcknowledgement();
            } catch (RemoteException | RuntimeException e) {
                handleRadioProxyExceptionForRR(rr, "sendAck", e);
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("sendAck: ");
                stringBuilder.append(e);
                riljLoge(stringBuilder.toString());
            }
        } else {
            Rlog.e(RILJ_LOG_TAG, "Error trying to send ack, radioProxy = null");
        }
        rr.release();
    }

    private WorkSource getDeafultWorkSourceIfInvalid(WorkSource workSource) {
        if (workSource == null) {
            return this.mRILDefaultWorkSource;
        }
        return workSource;
    }

    /* JADX WARNING: Missing block: B:32:?, code skipped:
            r8.mWakeLockType = r9;
     */
    /* JADX WARNING: Missing block: B:34:0x00ab, code skipped:
            return;
     */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private void acquireWakeLock(RILRequest rr, int wakeLockType) {
        synchronized (rr) {
            String str;
            StringBuilder stringBuilder;
            if (rr.mWakeLockType != -1) {
                str = RILJ_LOG_TAG;
                stringBuilder = new StringBuilder();
                stringBuilder.append("Failed to aquire wakelock for ");
                stringBuilder.append(rr.serialString());
                Rlog.d(str, stringBuilder.toString());
                return;
            }
            switch (wakeLockType) {
                case 0:
                    synchronized (this.mWakeLock) {
                        this.mWakeLock.acquire();
                        this.mWakeLockCount++;
                        this.mWlSequenceNum++;
                        if (!this.mClientWakelockTracker.isClientActive(rr.getWorkSourceClientId())) {
                            if (this.mActiveWakelockWorkSource != null) {
                                this.mActiveWakelockWorkSource.add(rr.mWorkSource);
                            } else {
                                this.mActiveWakelockWorkSource = rr.mWorkSource;
                            }
                            this.mWakeLock.setWorkSource(this.mActiveWakelockWorkSource);
                        }
                        this.mClientWakelockTracker.startTracking(rr.mClientId, rr.mRequest, rr.mSerial, this.mWakeLockCount);
                        Message msg = this.mRilHandler.obtainMessage(2);
                        msg.arg1 = this.mWlSequenceNum;
                        this.mRilHandler.sendMessageDelayed(msg, (long) this.mWakeLockTimeout);
                    }
                case 1:
                    synchronized (this.mAckWakeLock) {
                        this.mAckWakeLock.acquire();
                        this.mAckWlSequenceNum++;
                        Message msg2 = this.mRilHandler.obtainMessage(4);
                        msg2.arg1 = this.mAckWlSequenceNum;
                        this.mRilHandler.sendMessageDelayed(msg2, (long) this.mAckWakeLockTimeout);
                    }
                default:
                    str = RILJ_LOG_TAG;
                    stringBuilder = new StringBuilder();
                    stringBuilder.append("Acquiring Invalid Wakelock type ");
                    stringBuilder.append(wakeLockType);
                    Rlog.w(str, stringBuilder.toString());
                    return;
            }
        }
    }

    @VisibleForTesting
    public WakeLock getWakeLock(int wakeLockType) {
        return wakeLockType == 0 ? this.mWakeLock : this.mAckWakeLock;
    }

    @VisibleForTesting
    public RilHandler getRilHandler() {
        return this.mRilHandler;
    }

    @VisibleForTesting
    public SparseArray<RILRequest> getRilRequestList() {
        return this.mRequestList;
    }

    private void decrementWakeLock(RILRequest rr) {
        synchronized (rr) {
            switch (rr.mWakeLockType) {
                case -1:
                    break;
                case 0:
                    synchronized (this.mWakeLock) {
                        int i;
                        ClientWakelockTracker clientWakelockTracker = this.mClientWakelockTracker;
                        String str = rr.mClientId;
                        int i2 = rr.mRequest;
                        int i3 = rr.mSerial;
                        if (this.mWakeLockCount > 1) {
                            i = this.mWakeLockCount - 1;
                        } else {
                            i = 0;
                        }
                        clientWakelockTracker.stopTracking(str, i2, i3, i);
                        if (!(this.mClientWakelockTracker.isClientActive(rr.getWorkSourceClientId()) || this.mActiveWakelockWorkSource == null)) {
                            this.mActiveWakelockWorkSource.remove(rr.mWorkSource);
                            if (this.mActiveWakelockWorkSource.size() == 0) {
                                this.mActiveWakelockWorkSource = null;
                            }
                            this.mWakeLock.setWorkSource(this.mActiveWakelockWorkSource);
                        }
                        if (this.mWakeLockCount > 1) {
                            this.mWakeLockCount--;
                        } else {
                            this.mWakeLockCount = 0;
                            this.mWakeLock.release();
                        }
                    }
                    break;
                case 1:
                    break;
                default:
                    String str2 = RILJ_LOG_TAG;
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append("Decrementing Invalid Wakelock type ");
                    stringBuilder.append(rr.mWakeLockType);
                    Rlog.w(str2, stringBuilder.toString());
                    break;
            }
            rr.mWakeLockType = -1;
        }
    }

    private boolean clearWakeLock(int wakeLockType) {
        if (wakeLockType == 0) {
            synchronized (this.mWakeLock) {
                if (this.mWakeLockCount != 0 || this.mWakeLock.isHeld()) {
                    String str = RILJ_LOG_TAG;
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append("NOTE: mWakeLockCount is ");
                    stringBuilder.append(this.mWakeLockCount);
                    stringBuilder.append("at time of clearing");
                    Rlog.d(str, stringBuilder.toString());
                    this.mWakeLockCount = 0;
                    this.mWakeLock.release();
                    this.mClientWakelockTracker.stopTrackingAll();
                    this.mActiveWakelockWorkSource = null;
                    return true;
                }
                return false;
            }
        }
        synchronized (this.mAckWakeLock) {
            if (this.mAckWakeLock.isHeld()) {
                this.mAckWakeLock.release();
                return true;
            }
            return false;
        }
    }

    private void clearRequestList(int error, boolean loggable) {
        synchronized (this.mRequestList) {
            int count = this.mRequestList.size();
            if (loggable) {
                String str = RILJ_LOG_TAG;
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("clearRequestList  mWakeLockCount=");
                stringBuilder.append(this.mWakeLockCount);
                stringBuilder.append(" mRequestList=");
                stringBuilder.append(count);
                Rlog.d(str, stringBuilder.toString());
            }
            for (int i = 0; i < count; i++) {
                RILRequest rr = (RILRequest) this.mRequestList.valueAt(i);
                if (loggable) {
                    String str2 = RILJ_LOG_TAG;
                    StringBuilder stringBuilder2 = new StringBuilder();
                    stringBuilder2.append(i);
                    stringBuilder2.append(": [");
                    stringBuilder2.append(rr.mSerial);
                    stringBuilder2.append("] ");
                    stringBuilder2.append(requestToString(rr.mRequest));
                    Rlog.d(str2, stringBuilder2.toString());
                }
                rr.onError(error, null);
                decrementWakeLock(rr);
                rr.release();
            }
            this.mRequestList.clear();
        }
    }

    private RILRequest findAndRemoveRequestFromList(int serial) {
        RILRequest rr;
        synchronized (this.mRequestList) {
            rr = (RILRequest) this.mRequestList.get(serial);
            if (rr != null) {
                this.mRequestList.remove(serial);
            }
        }
        return rr;
    }

    private void addToRilHistogram(RILRequest rr) {
        int totalTime = (int) (SystemClock.elapsedRealtime() - rr.mStartTimeMs);
        synchronized (mRilTimeHistograms) {
            TelephonyHistogram entry = (TelephonyHistogram) mRilTimeHistograms.get(rr.mRequest);
            if (entry == null) {
                entry = new TelephonyHistogram(1, rr.mRequest, 5);
                mRilTimeHistograms.put(rr.mRequest, entry);
            }
            entry.addTimeTaken(totalTime);
        }
    }

    RadioCapability makeStaticRadioCapability() {
        int raf = 1;
        String rafString = this.mContext.getResources().getString(17039839);
        if (!TextUtils.isEmpty(rafString)) {
            raf = RadioAccessFamily.rafTypeFromString(rafString);
        }
        RadioCapability rc = new RadioCapability(this.mPhoneId != null ? this.mPhoneId.intValue() : 0, 0, 0, raf, "", 1);
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Faking RIL_REQUEST_GET_RADIO_CAPABILITY response using ");
        stringBuilder.append(raf);
        riljLog(stringBuilder.toString());
        return rc;
    }

    static String retToString(int req, Object ret) {
        if (ret == null) {
            return "";
        }
        if (!(req == 11 || req == 95 || req == 98 || req == 115 || req == 117 || req == 529)) {
            switch (req) {
                case 38:
                case 39:
                    break;
                default:
                    String s;
                    int length;
                    StringBuilder sb;
                    int i;
                    int i2;
                    StringBuilder sb2;
                    Iterator it;
                    if (ret instanceof int[]) {
                        int[] intArray = (int[]) ret;
                        length = intArray.length;
                        sb = new StringBuilder("{");
                        if (length > 0) {
                            i = 0 + 1;
                            sb.append(intArray[0]);
                            while (i < length) {
                                sb.append(", ");
                                i2 = i + 1;
                                sb.append(intArray[i]);
                                i = i2;
                            }
                        }
                        sb.append("}");
                        s = sb.toString();
                    } else if (ret instanceof String[]) {
                        String[] strings = (String[]) ret;
                        length = strings.length;
                        sb = new StringBuilder("{");
                        if (length > 0) {
                            i = 0 + 1;
                            sb.append(strings[0]);
                            while (i < length) {
                                sb.append(", ");
                                i2 = i + 1;
                                sb.append(strings[i]);
                                i = i2;
                            }
                        }
                        sb.append("}");
                        s = sb.toString();
                    } else if (req == 9) {
                        ArrayList<DriverCall> calls = (ArrayList) ret;
                        sb2 = new StringBuilder("{");
                        it = calls.iterator();
                        while (it.hasNext()) {
                            DriverCall dc = (DriverCall) it.next();
                            sb2.append("[");
                            sb2.append(dc);
                            sb2.append("] ");
                        }
                        sb2.append("}");
                        s = sb2.toString();
                    } else if (req == 75) {
                        ArrayList<NeighboringCellInfo> cells = (ArrayList) ret;
                        sb2 = new StringBuilder("{");
                        it = cells.iterator();
                        while (it.hasNext()) {
                            NeighboringCellInfo cell = (NeighboringCellInfo) it.next();
                            sb2.append("[");
                            sb2.append(cell);
                            sb2.append("] ");
                        }
                        sb2.append("}");
                        s = sb2.toString();
                    } else if (req == 33) {
                        sb = new StringBuilder("{");
                        for (Object append : (CallForwardInfo[]) ret) {
                            sb.append("[");
                            sb.append(append);
                            sb.append("] ");
                        }
                        sb.append("}");
                        s = sb.toString();
                    } else if (req == 124) {
                        ArrayList<HardwareConfig> hwcfgs = (ArrayList) ret;
                        sb2 = new StringBuilder(" ");
                        it = hwcfgs.iterator();
                        while (it.hasNext()) {
                            HardwareConfig hwcfg = (HardwareConfig) it.next();
                            sb2.append("[");
                            sb2.append(hwcfg);
                            sb2.append("] ");
                        }
                        s = sb2.toString();
                    } else {
                        s = HwTelephonyFactory.getHwNetworkManager().retToStringEx(req, ret);
                    }
                    return s;
            }
        }
        return "";
    }

    void writeMetricsNewSms(int tech, int format) {
        this.mMetrics.writeRilNewSms(this.mPhoneId.intValue(), tech, format);
    }

    void writeMetricsCallRing(char[] response) {
        this.mMetrics.writeRilCallRing(this.mPhoneId.intValue(), response);
    }

    void writeMetricsSrvcc(int state) {
        this.mMetrics.writeRilSrvcc(this.mPhoneId.intValue(), state);
    }

    void writeMetricsModemRestartEvent(String reason) {
        this.mMetrics.writeModemRestartEvent(this.mPhoneId.intValue(), reason);
    }

    void notifyRegistrantsRilConnectionChanged(int rilVer) {
        this.mRilVersion = rilVer;
        if (this.mRilConnectedRegistrants != null) {
            this.mRilConnectedRegistrants.notifyRegistrants(new AsyncResult(null, new Integer(rilVer), null));
        }
    }

    void notifyRegistrantsCdmaInfoRec(CdmaInformationRecords infoRec) {
        if (infoRec.record instanceof CdmaDisplayInfoRec) {
            if (this.mDisplayInfoRegistrants != null) {
                unsljLogRet(1027, infoRec.record);
                this.mDisplayInfoRegistrants.notifyRegistrants(new AsyncResult(null, infoRec.record, null));
            }
        } else if (infoRec.record instanceof CdmaSignalInfoRec) {
            if (this.mSignalInfoRegistrants != null) {
                unsljLogRet(1027, infoRec.record);
                this.mSignalInfoRegistrants.notifyRegistrants(new AsyncResult(null, infoRec.record, null));
            }
        } else if (infoRec.record instanceof CdmaNumberInfoRec) {
            if (this.mNumberInfoRegistrants != null) {
                unsljLogRet(1027, infoRec.record);
                this.mNumberInfoRegistrants.notifyRegistrants(new AsyncResult(null, infoRec.record, null));
            }
        } else if (infoRec.record instanceof CdmaRedirectingNumberInfoRec) {
            if (this.mRedirNumInfoRegistrants != null) {
                unsljLogRet(1027, infoRec.record);
                this.mRedirNumInfoRegistrants.notifyRegistrants(new AsyncResult(null, infoRec.record, null));
            }
        } else if (infoRec.record instanceof CdmaLineControlInfoRec) {
            if (this.mLineControlInfoRegistrants != null) {
                unsljLogRet(1027, infoRec.record);
                this.mLineControlInfoRegistrants.notifyRegistrants(new AsyncResult(null, infoRec.record, null));
            }
        } else if (infoRec.record instanceof CdmaT53ClirInfoRec) {
            if (this.mT53ClirInfoRegistrants != null) {
                unsljLogRet(1027, infoRec.record);
                this.mT53ClirInfoRegistrants.notifyRegistrants(new AsyncResult(null, infoRec.record, null));
            }
        } else if ((infoRec.record instanceof CdmaT53AudioControlInfoRec) && this.mT53AudCntrlInfoRegistrants != null) {
            unsljLogRet(1027, infoRec.record);
            this.mT53AudCntrlInfoRegistrants.notifyRegistrants(new AsyncResult(null, infoRec.record, null));
        }
    }

    static String requestToString(int request) {
        if (request == 800) {
            return "RIL_RESPONSE_ACKNOWLEDGEMENT";
        }
        switch (request) {
            case 1:
                return "GET_SIM_STATUS";
            case 2:
                return "ENTER_SIM_PIN";
            case 3:
                return "ENTER_SIM_PUK";
            case 4:
                return "ENTER_SIM_PIN2";
            case 5:
                return "ENTER_SIM_PUK2";
            case 6:
                return "CHANGE_SIM_PIN";
            case 7:
                return "CHANGE_SIM_PIN2";
            case 8:
                return "ENTER_NETWORK_DEPERSONALIZATION";
            case 9:
                return "GET_CURRENT_CALLS";
            case 10:
                return "DIAL";
            case 11:
                return "GET_IMSI";
            case 12:
                return "HANGUP";
            case 13:
                return "HANGUP_WAITING_OR_BACKGROUND";
            case 14:
                return "HANGUP_FOREGROUND_RESUME_BACKGROUND";
            case 15:
                return "REQUEST_SWITCH_WAITING_OR_HOLDING_AND_ACTIVE";
            case 16:
                return "CONFERENCE";
            case 17:
                return "UDUB";
            case 18:
                return "LAST_CALL_FAIL_CAUSE";
            case 19:
                return "SIGNAL_STRENGTH";
            case 20:
                return "VOICE_REGISTRATION_STATE";
            case 21:
                return "DATA_REGISTRATION_STATE";
            case 22:
                return "OPERATOR";
            case 23:
                return "RADIO_POWER";
            case 24:
                return "DTMF";
            case 25:
                return "SEND_SMS";
            case 26:
                return "SEND_SMS_EXPECT_MORE";
            case 27:
                return "SETUP_DATA_CALL";
            case 28:
                return "SIM_IO";
            case 29:
                return "SEND_USSD";
            case 30:
                return "CANCEL_USSD";
            case 31:
                return "GET_CLIR";
            case 32:
                return "SET_CLIR";
            case 33:
                return "QUERY_CALL_FORWARD_STATUS";
            case 34:
                return "SET_CALL_FORWARD";
            case 35:
                return "QUERY_CALL_WAITING";
            case 36:
                return "SET_CALL_WAITING";
            case 37:
                return "SMS_ACKNOWLEDGE";
            case 38:
                return "GET_IMEI";
            case 39:
                return "GET_IMEISV";
            case 40:
                return "ANSWER";
            case 41:
                return "DEACTIVATE_DATA_CALL";
            case 42:
                return "QUERY_FACILITY_LOCK";
            case 43:
                return "SET_FACILITY_LOCK";
            case 44:
                return "CHANGE_BARRING_PASSWORD";
            case 45:
                return "QUERY_NETWORK_SELECTION_MODE";
            case 46:
                return "SET_NETWORK_SELECTION_AUTOMATIC";
            case 47:
                return "SET_NETWORK_SELECTION_MANUAL";
            case 48:
                return "QUERY_AVAILABLE_NETWORKS ";
            case 49:
                return "DTMF_START";
            case 50:
                return "DTMF_STOP";
            case 51:
                return "BASEBAND_VERSION";
            case 52:
                return "SEPARATE_CONNECTION";
            case 53:
                return "SET_MUTE";
            case 54:
                return "GET_MUTE";
            case 55:
                return "QUERY_CLIP";
            case 56:
                return "LAST_DATA_CALL_FAIL_CAUSE";
            case 57:
                return "DATA_CALL_LIST";
            case 58:
                return "RESET_RADIO";
            case 59:
                return "OEM_HOOK_RAW";
            case 60:
                return "OEM_HOOK_STRINGS";
            case 61:
                return "SCREEN_STATE";
            case 62:
                return "SET_SUPP_SVC_NOTIFICATION";
            case 63:
                return "WRITE_SMS_TO_SIM";
            case 64:
                return "DELETE_SMS_ON_SIM";
            case 65:
                return "SET_BAND_MODE";
            case 66:
                return "QUERY_AVAILABLE_BAND_MODE";
            case 67:
                return "REQUEST_STK_GET_PROFILE";
            case 68:
                return "REQUEST_STK_SET_PROFILE";
            case 69:
                return "REQUEST_STK_SEND_ENVELOPE_COMMAND";
            case 70:
                return "REQUEST_STK_SEND_TERMINAL_RESPONSE";
            case 71:
                return "REQUEST_STK_HANDLE_CALL_SETUP_REQUESTED_FROM_SIM";
            case 72:
                return "REQUEST_EXPLICIT_CALL_TRANSFER";
            case 73:
                return "REQUEST_SET_PREFERRED_NETWORK_TYPE";
            case 74:
                return "REQUEST_GET_PREFERRED_NETWORK_TYPE";
            case 75:
                return "REQUEST_GET_NEIGHBORING_CELL_IDS";
            case 76:
                return "REQUEST_SET_LOCATION_UPDATES";
            case 77:
                return "RIL_REQUEST_CDMA_SET_SUBSCRIPTION_SOURCE";
            case 78:
                return "RIL_REQUEST_CDMA_SET_ROAMING_PREFERENCE";
            case 79:
                return "RIL_REQUEST_CDMA_QUERY_ROAMING_PREFERENCE";
            case RadioNVItems.RIL_NV_LTE_NEXT_SCAN /*80*/:
                return "RIL_REQUEST_SET_TTY_MODE";
            case 81:
                return "RIL_REQUEST_QUERY_TTY_MODE";
            case RadioNVItems.RIL_NV_LTE_BSR_MAX_TIME /*82*/:
                return "RIL_REQUEST_CDMA_SET_PREFERRED_VOICE_PRIVACY_MODE";
            case 83:
                return "RIL_REQUEST_CDMA_QUERY_PREFERRED_VOICE_PRIVACY_MODE";
            case 84:
                return "RIL_REQUEST_CDMA_FLASH";
            case 85:
                return "RIL_REQUEST_CDMA_BURST_DTMF";
            case 86:
                return "RIL_REQUEST_CDMA_VALIDATE_AND_WRITE_AKEY";
            case 87:
                return "RIL_REQUEST_CDMA_SEND_SMS";
            case 88:
                return "RIL_REQUEST_CDMA_SMS_ACKNOWLEDGE";
            case 89:
                return "RIL_REQUEST_GSM_GET_BROADCAST_CONFIG";
            case 90:
                return "RIL_REQUEST_GSM_SET_BROADCAST_CONFIG";
            case 91:
                return "RIL_REQUEST_GSM_BROADCAST_ACTIVATION";
            case 92:
                return "RIL_REQUEST_CDMA_GET_BROADCAST_CONFIG";
            case 93:
                return "RIL_REQUEST_CDMA_SET_BROADCAST_CONFIG";
            case 94:
                return "RIL_REQUEST_CDMA_BROADCAST_ACTIVATION";
            case 95:
                return "RIL_REQUEST_CDMA_SUBSCRIPTION";
            case 96:
                return "RIL_REQUEST_CDMA_WRITE_SMS_TO_RUIM";
            case 97:
                return "RIL_REQUEST_CDMA_DELETE_SMS_ON_RUIM";
            case 98:
                return "RIL_REQUEST_DEVICE_IDENTITY";
            case 99:
                return "REQUEST_EXIT_EMERGENCY_CALLBACK_MODE";
            case 100:
                return "RIL_REQUEST_GET_SMSC_ADDRESS";
            case 101:
                return "RIL_REQUEST_SET_SMSC_ADDRESS";
            case 102:
                return "RIL_REQUEST_REPORT_SMS_MEMORY_STATUS";
            case 103:
                return "RIL_REQUEST_REPORT_STK_SERVICE_IS_RUNNING";
            case AbstractPhoneBase.EVENT_ECC_NUM /*104*/:
                return "RIL_REQUEST_CDMA_GET_SUBSCRIPTION_SOURCE";
            case AbstractPhoneBase.EVENT_GET_IMSI_DONE /*105*/:
                return "RIL_REQUEST_ISIM_AUTHENTICATION";
            case 106:
                return "RIL_REQUEST_ACKNOWLEDGE_INCOMING_GSM_SMS_WITH_PDU";
            case 107:
                return "RIL_REQUEST_STK_SEND_ENVELOPE_WITH_STATUS";
            case AbstractPhoneBase.EVENT_GET_LTE_RELEASE_VERSION_DONE /*108*/:
                return "RIL_REQUEST_VOICE_RADIO_TECH";
            case 109:
                return "RIL_REQUEST_GET_CELL_INFO_LIST";
            case 110:
                return "RIL_REQUEST_SET_CELL_INFO_LIST_RATE";
            case 111:
                return "RIL_REQUEST_SET_INITIAL_ATTACH_APN";
            case 112:
                return "RIL_REQUEST_IMS_REGISTRATION_STATE";
            case 113:
                return "RIL_REQUEST_IMS_SEND_SMS";
            case 114:
                return "RIL_REQUEST_SIM_TRANSMIT_APDU_BASIC";
            case 115:
                return "RIL_REQUEST_SIM_OPEN_CHANNEL";
            case 116:
                return "RIL_REQUEST_SIM_CLOSE_CHANNEL";
            case 117:
                return "RIL_REQUEST_SIM_TRANSMIT_APDU_CHANNEL";
            case 118:
                return "RIL_REQUEST_NV_READ_ITEM";
            case 119:
                return "RIL_REQUEST_NV_WRITE_ITEM";
            case 120:
                return "RIL_REQUEST_NV_WRITE_CDMA_PRL";
            case 121:
                return "RIL_REQUEST_NV_RESET_CONFIG";
            case 122:
                return "RIL_REQUEST_SET_UICC_SUBSCRIPTION";
            case 123:
                return "RIL_REQUEST_ALLOW_DATA";
            case 124:
                return "GET_HARDWARE_CONFIG";
            case 125:
                return "RIL_REQUEST_SIM_AUTHENTICATION";
            default:
                switch (request) {
                    case 128:
                        return "RIL_REQUEST_SET_DATA_PROFILE";
                    case 129:
                        return "RIL_REQUEST_SHUTDOWN";
                    case 130:
                        return "RIL_REQUEST_GET_RADIO_CAPABILITY";
                    case 131:
                        return "RIL_REQUEST_SET_RADIO_CAPABILITY";
                    case 132:
                        return "RIL_REQUEST_START_LCE";
                    case 133:
                        return "RIL_REQUEST_STOP_LCE";
                    case 134:
                        return "RIL_REQUEST_PULL_LCEDATA";
                    case 135:
                        return "RIL_REQUEST_GET_ACTIVITY_INFO";
                    case 136:
                        return "RIL_REQUEST_SET_ALLOWED_CARRIERS";
                    case 137:
                        return "RIL_REQUEST_GET_ALLOWED_CARRIERS";
                    case 138:
                        return "RIL_REQUEST_SEND_DEVICE_STATE";
                    case 139:
                        return "RIL_REQUEST_SET_UNSOLICITED_RESPONSE_FILTER";
                    case 140:
                        return "RIL_REQUEST_SET_SIM_CARD_POWER";
                    case 141:
                        return "RIL_REQUEST_SET_CARRIER_INFO_IMSI_ENCRYPTION";
                    case 142:
                        return "RIL_REQUEST_START_NETWORK_SCAN";
                    case 143:
                        return "RIL_REQUEST_STOP_NETWORK_SCAN";
                    case 144:
                        return "RIL_REQUEST_GET_SLOT_STATUS";
                    case 145:
                        return "RIL_REQUEST_SET_LOGICAL_TO_PHYSICAL_SLOT_MAPPING";
                    case 146:
                        return "RIL_REQUEST_START_KEEPALIVE";
                    case 147:
                        return "RIL_REQUEST_STOP_KEEPALIVE";
                    case 148:
                        return "RIL_REQUEST_SET_SIGNAL_STRENGTH_REPORTING_CRITERIA";
                    case 149:
                        return "RIL_REQUEST_SET_LINK_CAPACITY_REPORTING_CRITERIA";
                    default:
                        return HwTelephonyFactory.getHwTelephonyBaseManager().requestToStringEx(request);
                }
        }
    }

    static String responseToString(int request) {
        switch (request) {
            case 1000:
                return "UNSOL_RESPONSE_RADIO_STATE_CHANGED";
            case 1001:
                return "UNSOL_RESPONSE_CALL_STATE_CHANGED";
            case 1002:
                return "UNSOL_RESPONSE_NETWORK_STATE_CHANGED";
            case 1003:
                return "UNSOL_RESPONSE_NEW_SMS";
            case 1004:
                return "UNSOL_RESPONSE_NEW_SMS_STATUS_REPORT";
            case 1005:
                return "UNSOL_RESPONSE_NEW_SMS_ON_SIM";
            case 1006:
                return "UNSOL_ON_USSD";
            case 1007:
                return "UNSOL_ON_USSD_REQUEST";
            case 1008:
                return "UNSOL_NITZ_TIME_RECEIVED";
            case 1009:
                return "UNSOL_SIGNAL_STRENGTH";
            case 1010:
                return "UNSOL_DATA_CALL_LIST_CHANGED";
            case 1011:
                return "UNSOL_SUPP_SVC_NOTIFICATION";
            case 1012:
                return "UNSOL_STK_SESSION_END";
            case 1013:
                return "UNSOL_STK_PROACTIVE_COMMAND";
            case 1014:
                return "UNSOL_STK_EVENT_NOTIFY";
            case CharacterSets.UTF_16 /*1015*/:
                return "UNSOL_STK_CALL_SETUP";
            case 1016:
                return "UNSOL_SIM_SMS_STORAGE_FULL";
            case 1017:
                return "UNSOL_SIM_REFRESH";
            case 1018:
                return "UNSOL_CALL_RING";
            case 1019:
                return "UNSOL_RESPONSE_SIM_STATUS_CHANGED";
            case 1020:
                return "UNSOL_RESPONSE_CDMA_NEW_SMS";
            case 1021:
                return "UNSOL_RESPONSE_NEW_BROADCAST_SMS";
            case 1022:
                return "UNSOL_CDMA_RUIM_SMS_STORAGE_FULL";
            case ApnTypes.ALL /*1023*/:
                return "UNSOL_RESTRICTED_STATE_CHANGED";
            case android.hardware.radio.V1_0.RadioAccessFamily.HSUPA /*1024*/:
                return "UNSOL_ENTER_EMERGENCY_CALLBACK_MODE";
            case 1025:
                return "UNSOL_CDMA_CALL_WAITING";
            case 1026:
                return "UNSOL_CDMA_OTA_PROVISION_STATUS";
            case 1027:
                return "UNSOL_CDMA_INFO_REC";
            case 1028:
                return "UNSOL_OEM_HOOK_RAW";
            case 1029:
                return "UNSOL_RINGBACK_TONE";
            case 1030:
                return "UNSOL_RESEND_INCALL_MUTE";
            case 1031:
                return "CDMA_SUBSCRIPTION_SOURCE_CHANGED";
            case 1032:
                return "UNSOL_CDMA_PRL_CHANGED";
            case 1033:
                return "UNSOL_EXIT_EMERGENCY_CALLBACK_MODE";
            case 1034:
                return "UNSOL_RIL_CONNECTED";
            case 1035:
                return "UNSOL_VOICE_RADIO_TECH_CHANGED";
            case 1036:
                return "UNSOL_CELL_INFO_LIST";
            case 1037:
                return "UNSOL_RESPONSE_IMS_NETWORK_STATE_CHANGED";
            case 1038:
                return "RIL_UNSOL_UICC_SUBSCRIPTION_STATUS_CHANGED";
            case 1039:
                return "UNSOL_SRVCC_STATE_NOTIFY";
            case 1040:
                return "RIL_UNSOL_HARDWARE_CONFIG_CHANGED";
            case 1042:
                return "RIL_UNSOL_RADIO_CAPABILITY";
            case 1043:
                return "UNSOL_ON_SS";
            case 1044:
                return "UNSOL_STK_CC_ALPHA_NOTIFY";
            case 1045:
                return "UNSOL_LCE_INFO_RECV";
            case 1046:
                return "UNSOL_PCO_DATA";
            case 1047:
                return "UNSOL_MODEM_RESTART";
            case 1048:
                return "RIL_UNSOL_CARRIER_INFO_IMSI_ENCRYPTION";
            case 1049:
                return "RIL_UNSOL_NETWORK_SCAN_RESULT";
            case 1050:
                return "RIL_UNSOL_ICC_SLOT_STATUS";
            case 1051:
                return "RIL_UNSOL_KEEPALIVE_STATUS";
            case 1052:
                return "RIL_UNSOL_PHYSICAL_CHANNEL_CONFIG";
            default:
                return HwTelephonyFactory.getHwTelephonyBaseManager().responseToStringEx(request);
        }
    }

    void riljLog(String msg) {
        String stringBuilder;
        String str = RILJ_LOG_TAG;
        StringBuilder stringBuilder2 = new StringBuilder();
        stringBuilder2.append(msg);
        if (this.mPhoneId != null) {
            StringBuilder stringBuilder3 = new StringBuilder();
            stringBuilder3.append(" [SUB");
            stringBuilder3.append(this.mPhoneId);
            stringBuilder3.append("]");
            stringBuilder = stringBuilder3.toString();
        } else {
            stringBuilder = "";
        }
        stringBuilder2.append(stringBuilder);
        Rlog.d(str, stringBuilder2.toString());
    }

    void riljLoge(String msg) {
        String stringBuilder;
        String str = RILJ_LOG_TAG;
        StringBuilder stringBuilder2 = new StringBuilder();
        stringBuilder2.append(msg);
        if (this.mPhoneId != null) {
            StringBuilder stringBuilder3 = new StringBuilder();
            stringBuilder3.append(" [SUB");
            stringBuilder3.append(this.mPhoneId);
            stringBuilder3.append("]");
            stringBuilder = stringBuilder3.toString();
        } else {
            stringBuilder = "";
        }
        stringBuilder2.append(stringBuilder);
        Rlog.e(str, stringBuilder2.toString());
    }

    void riljLoge(String msg, Exception e) {
        String stringBuilder;
        String str = RILJ_LOG_TAG;
        StringBuilder stringBuilder2 = new StringBuilder();
        stringBuilder2.append(msg);
        if (this.mPhoneId != null) {
            StringBuilder stringBuilder3 = new StringBuilder();
            stringBuilder3.append(" [SUB");
            stringBuilder3.append(this.mPhoneId);
            stringBuilder3.append("]");
            stringBuilder = stringBuilder3.toString();
        } else {
            stringBuilder = "";
        }
        stringBuilder2.append(stringBuilder);
        Rlog.e(str, stringBuilder2.toString(), e);
    }

    void riljLogv(String msg) {
        String stringBuilder;
        String str = RILJ_LOG_TAG;
        StringBuilder stringBuilder2 = new StringBuilder();
        stringBuilder2.append(msg);
        if (this.mPhoneId != null) {
            StringBuilder stringBuilder3 = new StringBuilder();
            stringBuilder3.append(" [SUB");
            stringBuilder3.append(this.mPhoneId);
            stringBuilder3.append("]");
            stringBuilder = stringBuilder3.toString();
        } else {
            stringBuilder = "";
        }
        stringBuilder2.append(stringBuilder);
        Rlog.v(str, stringBuilder2.toString());
    }

    void unsljLog(int response) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("[UNSL]< ");
        stringBuilder.append(responseToString(response));
        riljLog(stringBuilder.toString());
    }

    void unsljLogMore(int response, String more) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("[UNSL]< ");
        stringBuilder.append(responseToString(response));
        stringBuilder.append(" ");
        stringBuilder.append(more);
        riljLog(stringBuilder.toString());
    }

    void unsljLogRet(int response, Object ret) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("[UNSL]< ");
        stringBuilder.append(responseToString(response));
        stringBuilder.append(" ");
        stringBuilder.append(retToString(response, ret));
        riljLog(stringBuilder.toString());
    }

    void unsljLogvRet(int response, Object ret) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("[UNSL]< ");
        stringBuilder.append(responseToString(response));
        stringBuilder.append(" ");
        stringBuilder.append(retToString(response, ret));
        riljLogv(stringBuilder.toString());
    }

    public void setPhoneType(int phoneType) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("setPhoneType=");
        stringBuilder.append(phoneType);
        stringBuilder.append(" old value=");
        stringBuilder.append(this.mPhoneType);
        riljLog(stringBuilder.toString());
        this.mPhoneType = phoneType;
    }

    public void testingEmergencyCall() {
        riljLog("testingEmergencyCall");
        this.mTestingEmergencyCall.set(true);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("RIL: ");
        stringBuilder.append(this);
        pw.println(stringBuilder.toString());
        stringBuilder = new StringBuilder();
        stringBuilder.append(" mWakeLock=");
        stringBuilder.append(this.mWakeLock);
        pw.println(stringBuilder.toString());
        stringBuilder = new StringBuilder();
        stringBuilder.append(" mWakeLockTimeout=");
        stringBuilder.append(this.mWakeLockTimeout);
        pw.println(stringBuilder.toString());
        synchronized (this.mRequestList) {
            StringBuilder stringBuilder2;
            synchronized (this.mWakeLock) {
                stringBuilder2 = new StringBuilder();
                stringBuilder2.append(" mWakeLockCount=");
                stringBuilder2.append(this.mWakeLockCount);
                pw.println(stringBuilder2.toString());
            }
            int count = this.mRequestList.size();
            stringBuilder2 = new StringBuilder();
            stringBuilder2.append(" mRequestList count=");
            stringBuilder2.append(count);
            pw.println(stringBuilder2.toString());
            for (int i = 0; i < count; i++) {
                RILRequest rr = (RILRequest) this.mRequestList.valueAt(i);
                StringBuilder stringBuilder3 = new StringBuilder();
                stringBuilder3.append("  [");
                stringBuilder3.append(rr.mSerial);
                stringBuilder3.append("] ");
                stringBuilder3.append(requestToString(rr.mRequest));
                pw.println(stringBuilder3.toString());
            }
        }
        stringBuilder = new StringBuilder();
        stringBuilder.append(" mLastNITZTimeInfo=");
        stringBuilder.append(Arrays.toString(this.mLastNITZTimeInfo));
        pw.println(stringBuilder.toString());
        stringBuilder = new StringBuilder();
        stringBuilder.append(" mTestingEmergencyCall=");
        stringBuilder.append(this.mTestingEmergencyCall.get());
        pw.println(stringBuilder.toString());
        this.mClientWakelockTracker.dumpClientRequestTracker(pw);
    }

    public List<ClientRequestStats> getClientRequestStats() {
        return this.mClientWakelockTracker.getClientRequestStats();
    }

    public static void appendPrimitiveArrayToArrayList(byte[] src, ArrayList<Byte> dst) {
        for (byte b : src) {
            dst.add(Byte.valueOf(b));
        }
    }

    public static ArrayList<Byte> primitiveArrayToArrayList(byte[] arr) {
        ArrayList<Byte> arrayList = new ArrayList(arr.length);
        for (byte b : arr) {
            arrayList.add(Byte.valueOf(b));
        }
        return arrayList;
    }

    public static ArrayList<Integer> primitiveArrayToArrayList(int[] arr) {
        ArrayList<Integer> arrayList = new ArrayList(arr.length);
        for (int i : arr) {
            arrayList.add(Integer.valueOf(i));
        }
        return arrayList;
    }

    public static byte[] arrayListToPrimitiveArray(ArrayList<Byte> bytes) {
        byte[] ret = new byte[bytes.size()];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = ((Byte) bytes.get(i)).byteValue();
        }
        return ret;
    }

    static ArrayList<HardwareConfig> convertHalHwConfigList(ArrayList<HardwareConfig> hwListRil, RIL ril) {
        ArrayList<HardwareConfig> response = new ArrayList(hwListRil.size());
        Iterator it = hwListRil.iterator();
        while (it.hasNext()) {
            HardwareConfig hw;
            HardwareConfig hwRil = (HardwareConfig) it.next();
            int type = hwRil.type;
            switch (type) {
                case 0:
                    HardwareConfig hw2 = new HardwareConfig(type);
                    HardwareConfigModem hwModem = (HardwareConfigModem) hwRil.modem.get(0);
                    hw2.assignModem(hwRil.uuid, hwRil.state, hwModem.rilModel, hwModem.rat, hwModem.maxVoice, hwModem.maxData, hwModem.maxStandby);
                    hw = hw2;
                    break;
                case 1:
                    hw = new HardwareConfig(type);
                    hw.assignSim(hwRil.uuid, hwRil.state, ((HardwareConfigSim) hwRil.sim.get(0)).modemUuid);
                    break;
                default:
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append("RIL_REQUEST_GET_HARDWARE_CONFIG invalid hardward type:");
                    stringBuilder.append(type);
                    throw new RuntimeException(stringBuilder.toString());
            }
            response.add(hw);
        }
        return response;
    }

    static RadioCapability convertHalRadioCapability(RadioCapability rcRil, RIL ril) {
        int session = rcRil.session;
        int phase = rcRil.phase;
        int rat = rcRil.raf;
        String logicModemUuid = rcRil.logicalModemUuid;
        int status = rcRil.status;
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("convertHalRadioCapability: session=");
        stringBuilder.append(session);
        stringBuilder.append(", phase=");
        stringBuilder.append(phase);
        stringBuilder.append(", rat=");
        stringBuilder.append(rat);
        stringBuilder.append(", logicModemUuid=");
        stringBuilder.append(logicModemUuid);
        stringBuilder.append(", status=");
        stringBuilder.append(status);
        ril.riljLog(stringBuilder.toString());
        return new RadioCapability(ril.mPhoneId.intValue(), session, phase, rat, logicModemUuid, status);
    }

    static LinkCapacityEstimate convertHalLceData(LceDataInfo halData, RIL ril) {
        int i;
        int i2 = halData.lastHopCapacityKbps;
        int toUnsignedInt = Byte.toUnsignedInt(halData.confidenceLevel);
        if (halData.lceSuspended) {
            i = 1;
        } else {
            i = 0;
        }
        LinkCapacityEstimate lce = new LinkCapacityEstimate(i2, toUnsignedInt, i);
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("LCE capacity information received:");
        stringBuilder.append(lce);
        ril.riljLog(stringBuilder.toString());
        return lce;
    }

    static LinkCapacityEstimate convertHalLceData(LinkCapacityEstimate halData, RIL ril) {
        LinkCapacityEstimate lce = new LinkCapacityEstimate(halData.downlinkCapacityKbps, halData.uplinkCapacityKbps);
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("LCE capacity information received:");
        stringBuilder.append(lce);
        ril.riljLog(stringBuilder.toString());
        return lce;
    }

    static void writeToParcelForGsm(Parcel p, int lac, int cid, int arfcn, int bsic, String mcc, String mnc, String al, String as, int ss, int ber, int ta) {
        p.writeInt(1);
        p.writeString(mcc);
        p.writeString(mnc);
        p.writeString(al);
        p.writeString(as);
        p.writeInt(lac);
        p.writeInt(cid);
        p.writeInt(arfcn);
        p.writeInt(bsic);
        p.writeInt(ss);
        p.writeInt(ber);
        p.writeInt(ta);
    }

    static void writeToParcelForCdma(Parcel p, int ni, int si, int bsi, int lon, int lat, String al, String as, int dbm, int ecio, int eDbm, int eEcio, int eSnr) {
        Parcel parcel = p;
        new CellIdentityCdma(ni, si, bsi, lon, lat, al, as).writeToParcel(parcel, 0);
        new CellSignalStrengthCdma(dbm, ecio, eDbm, eEcio, eSnr).writeToParcel(parcel, 0);
    }

    static void writeToParcelForLte(Parcel p, int ci, int pci, int tac, int earfcn, int bandwidth, String mcc, String mnc, String al, String as, int ss, int rsrp, int rsrq, int rssnr, int cqi, int ta) {
        Parcel parcel = p;
        parcel.writeInt(3);
        parcel.writeString(mcc);
        parcel.writeString(mnc);
        parcel.writeString(al);
        parcel.writeString(as);
        p.writeInt(ci);
        parcel.writeInt(pci);
        parcel.writeInt(tac);
        parcel.writeInt(earfcn);
        parcel.writeInt(bandwidth);
        parcel.writeInt(ss);
        parcel.writeInt(rsrp);
        parcel.writeInt(rsrq);
        parcel.writeInt(rssnr);
        parcel.writeInt(cqi);
        parcel.writeInt(ta);
    }

    static void writeToParcelForWcdma(Parcel p, int lac, int cid, int psc, int uarfcn, String mcc, String mnc, String al, String as, int ss, int ber) {
        p.writeInt(4);
        p.writeString(mcc);
        p.writeString(mnc);
        p.writeString(al);
        p.writeString(as);
        p.writeInt(lac);
        p.writeInt(cid);
        p.writeInt(psc);
        p.writeInt(uarfcn);
        p.writeInt(ss);
        p.writeInt(ber);
    }

    @VisibleForTesting
    public static ArrayList<CellInfo> convertHalCellInfoList(ArrayList<android.hardware.radio.V1_0.CellInfo> records) {
        ArrayList<CellInfo> response = new ArrayList(records.size());
        Iterator it = records.iterator();
        while (it.hasNext()) {
            Iterator it2;
            int i;
            Parcel p;
            Parcel parcel;
            android.hardware.radio.V1_0.CellInfo record = (android.hardware.radio.V1_0.CellInfo) it.next();
            Parcel p2 = Parcel.obtain();
            p2.writeInt(record.cellInfoType);
            p2.writeInt(record.registered);
            p2.writeInt(record.timeStampType);
            p2.writeLong(record.timeStamp);
            p2.writeInt(KeepaliveStatus.INVALID_HANDLE);
            int i2;
            int i3;
            switch (record.cellInfoType) {
                case 1:
                    it2 = it;
                    i = 0;
                    p = p2;
                    CellInfoGsm cellInfoGsm = (CellInfoGsm) record.gsm.get(i);
                    parcel = p;
                    writeToParcelForGsm(parcel, cellInfoGsm.cellIdentityGsm.lac, cellInfoGsm.cellIdentityGsm.cid, cellInfoGsm.cellIdentityGsm.arfcn, Byte.toUnsignedInt(cellInfoGsm.cellIdentityGsm.bsic), cellInfoGsm.cellIdentityGsm.mcc, cellInfoGsm.cellIdentityGsm.mnc, "", "", cellInfoGsm.signalStrengthGsm.signalStrength, cellInfoGsm.signalStrengthGsm.bitErrorRate, cellInfoGsm.signalStrengthGsm.timingAdvance);
                    break;
                case 2:
                    it2 = it;
                    p = p2;
                    CellInfoCdma cellInfoCdma = (CellInfoCdma) record.cdma.get(0);
                    int i4 = cellInfoCdma.signalStrengthCdma.dbm;
                    int i5 = cellInfoCdma.signalStrengthCdma.ecio;
                    int i6 = cellInfoCdma.signalStrengthEvdo.dbm;
                    i2 = cellInfoCdma.signalStrengthEvdo.ecio;
                    i3 = cellInfoCdma.signalStrengthEvdo.signalNoiseRatio;
                    i = 0;
                    writeToParcelForCdma(p, cellInfoCdma.cellIdentityCdma.networkId, cellInfoCdma.cellIdentityCdma.systemId, cellInfoCdma.cellIdentityCdma.baseStationId, cellInfoCdma.cellIdentityCdma.longitude, cellInfoCdma.cellIdentityCdma.latitude, "", "", i4, i5, i6, i2, i3);
                    break;
                case 3:
                    CellInfoLte cellInfoLte = (CellInfoLte) record.lte.get(0);
                    int i7 = cellInfoLte.signalStrengthLte.signalStrength;
                    i2 = cellInfoLte.signalStrengthLte.rsrp;
                    int i8 = cellInfoLte.signalStrengthLte.rsrq;
                    it2 = it;
                    i = cellInfoLte.signalStrengthLte.rssnr;
                    int i9 = i7;
                    int i10 = cellInfoLte.signalStrengthLte.cqi;
                    int i11 = cellInfoLte.signalStrengthLte.timingAdvance;
                    i3 = i9;
                    p = p2;
                    writeToParcelForLte(p2, cellInfoLte.cellIdentityLte.ci, cellInfoLte.cellIdentityLte.pci, cellInfoLte.cellIdentityLte.tac, cellInfoLte.cellIdentityLte.earfcn, KeepaliveStatus.INVALID_HANDLE, cellInfoLte.cellIdentityLte.mcc, cellInfoLte.cellIdentityLte.mnc, "", "", i3, i2, i8, i, i10, i11);
                    i = 0;
                    break;
                case 4:
                    CellInfoWcdma cellInfoWcdma = (CellInfoWcdma) record.wcdma.get(0);
                    parcel = p2;
                    writeToParcelForWcdma(parcel, cellInfoWcdma.cellIdentityWcdma.lac, cellInfoWcdma.cellIdentityWcdma.cid, cellInfoWcdma.cellIdentityWcdma.psc, cellInfoWcdma.cellIdentityWcdma.uarfcn, cellInfoWcdma.cellIdentityWcdma.mcc, cellInfoWcdma.cellIdentityWcdma.mnc, "", "", cellInfoWcdma.signalStrengthWcdma.signalStrength, cellInfoWcdma.signalStrengthWcdma.bitErrorRate);
                    it2 = it;
                    i = 0;
                    p = p2;
                    break;
                default:
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append("unexpected cellinfotype: ");
                    stringBuilder.append(record.cellInfoType);
                    throw new RuntimeException(stringBuilder.toString());
            }
            parcel = p;
            parcel.setDataPosition(i);
            CellInfo InfoRec = (CellInfo) CellInfo.CREATOR.createFromParcel(parcel);
            parcel.recycle();
            response.add(InfoRec);
            it = it2;
        }
        return response;
    }

    @VisibleForTesting
    public static ArrayList<CellInfo> convertHalCellInfoList_1_2(ArrayList<android.hardware.radio.V1_2.CellInfo> records) {
        ArrayList<CellInfo> response = new ArrayList(records.size());
        Iterator it = records.iterator();
        while (it.hasNext()) {
            ArrayList<CellInfo> response2;
            Iterator it2;
            Parcel p;
            android.hardware.radio.V1_2.CellInfo record = (android.hardware.radio.V1_2.CellInfo) it.next();
            Parcel p2 = Parcel.obtain();
            p2.writeInt(record.cellInfoType);
            p2.writeInt(record.registered);
            p2.writeInt(record.timeStampType);
            p2.writeLong(record.timeStamp);
            p2.writeInt(record.connectionStatus);
            switch (record.cellInfoType) {
                case 1:
                    response2 = response;
                    it2 = it;
                    response = null;
                    p = p2;
                    android.hardware.radio.V1_2.CellInfoGsm cellInfoGsm = (android.hardware.radio.V1_2.CellInfoGsm) record.gsm.get(response);
                    writeToParcelForGsm(p, cellInfoGsm.cellIdentityGsm.base.lac, cellInfoGsm.cellIdentityGsm.base.cid, cellInfoGsm.cellIdentityGsm.base.arfcn, Byte.toUnsignedInt(cellInfoGsm.cellIdentityGsm.base.bsic), cellInfoGsm.cellIdentityGsm.base.mcc, cellInfoGsm.cellIdentityGsm.base.mnc, cellInfoGsm.cellIdentityGsm.operatorNames.alphaLong, cellInfoGsm.cellIdentityGsm.operatorNames.alphaShort, cellInfoGsm.signalStrengthGsm.signalStrength, cellInfoGsm.signalStrengthGsm.bitErrorRate, cellInfoGsm.signalStrengthGsm.timingAdvance);
                    break;
                case 2:
                    response2 = response;
                    it2 = it;
                    response = null;
                    p = p2;
                    android.hardware.radio.V1_2.CellInfoCdma cellInfoCdma = (android.hardware.radio.V1_2.CellInfoCdma) record.cdma.get(response);
                    writeToParcelForCdma(p, cellInfoCdma.cellIdentityCdma.base.networkId, cellInfoCdma.cellIdentityCdma.base.systemId, cellInfoCdma.cellIdentityCdma.base.baseStationId, cellInfoCdma.cellIdentityCdma.base.longitude, cellInfoCdma.cellIdentityCdma.base.latitude, cellInfoCdma.cellIdentityCdma.operatorNames.alphaLong, cellInfoCdma.cellIdentityCdma.operatorNames.alphaShort, cellInfoCdma.signalStrengthCdma.dbm, cellInfoCdma.signalStrengthCdma.ecio, cellInfoCdma.signalStrengthEvdo.dbm, cellInfoCdma.signalStrengthEvdo.ecio, cellInfoCdma.signalStrengthEvdo.signalNoiseRatio);
                    break;
                case 3:
                    android.hardware.radio.V1_2.CellInfoLte cellInfoLte = (android.hardware.radio.V1_2.CellInfoLte) record.lte.get(0);
                    int i = cellInfoLte.cellIdentityLte.base.ci;
                    int i2 = cellInfoLte.cellIdentityLte.base.pci;
                    int i3 = cellInfoLte.cellIdentityLte.base.tac;
                    int i4 = cellInfoLte.cellIdentityLte.base.earfcn;
                    int i5 = cellInfoLte.cellIdentityLte.bandwidth;
                    String str = cellInfoLte.cellIdentityLte.base.mcc;
                    String str2 = cellInfoLte.cellIdentityLte.base.mnc;
                    String str3 = cellInfoLte.cellIdentityLte.operatorNames.alphaLong;
                    String str4 = cellInfoLte.cellIdentityLte.operatorNames.alphaShort;
                    int i6 = cellInfoLte.signalStrengthLte.signalStrength;
                    int i7 = cellInfoLte.signalStrengthLte.rsrp;
                    it2 = it;
                    int i8 = cellInfoLte.signalStrengthLte.rsrq;
                    int i9 = i6;
                    int i10 = cellInfoLte.signalStrengthLte.rssnr;
                    int i11 = i9;
                    int i12 = i10;
                    response2 = response;
                    response = null;
                    p = p2;
                    writeToParcelForLte(p2, i, i2, i3, i4, i5, str, str2, str3, str4, i11, i7, i8, i12, cellInfoLte.signalStrengthLte.cqi, cellInfoLte.signalStrengthLte.timingAdvance);
                    break;
                case 4:
                    android.hardware.radio.V1_2.CellInfoWcdma cellInfoWcdma = (android.hardware.radio.V1_2.CellInfoWcdma) record.wcdma.get(0);
                    writeToParcelForWcdma(p2, cellInfoWcdma.cellIdentityWcdma.base.lac, cellInfoWcdma.cellIdentityWcdma.base.cid, cellInfoWcdma.cellIdentityWcdma.base.psc, cellInfoWcdma.cellIdentityWcdma.base.uarfcn, cellInfoWcdma.cellIdentityWcdma.base.mcc, cellInfoWcdma.cellIdentityWcdma.base.mnc, cellInfoWcdma.cellIdentityWcdma.operatorNames.alphaLong, cellInfoWcdma.cellIdentityWcdma.operatorNames.alphaShort, cellInfoWcdma.signalStrengthWcdma.base.signalStrength, cellInfoWcdma.signalStrengthWcdma.base.bitErrorRate);
                    response2 = response;
                    it2 = it;
                    response = null;
                    p = p2;
                    break;
                default:
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append("unexpected cellinfotype: ");
                    stringBuilder.append(record.cellInfoType);
                    throw new RuntimeException(stringBuilder.toString());
            }
            Parcel p3 = p;
            p3.setDataPosition(response);
            CellInfo response3 = (CellInfo) CellInfo.CREATOR.createFromParcel(p3);
            p3.recycle();
            ArrayList<CellInfo> response4 = response2;
            response4.add(response3);
            response = response4;
            it = it2;
        }
        return response;
    }

    @VisibleForTesting
    public static SignalStrength convertHalSignalStrength(android.hardware.radio.V1_0.SignalStrength signalStrength) {
        android.hardware.radio.V1_0.SignalStrength signalStrength2 = signalStrength;
        int tdscdmaRscp_1_2 = 255;
        if (signalStrength2.tdScdma.rscp >= 25 && signalStrength2.tdScdma.rscp <= 120) {
            tdscdmaRscp_1_2 = (-signalStrength2.tdScdma.rscp) + 120;
        }
        return new SignalStrength(signalStrength2.gw.signalStrength, signalStrength2.gw.bitErrorRate, signalStrength2.cdma.dbm, signalStrength2.cdma.ecio, signalStrength2.evdo.dbm, signalStrength2.evdo.ecio, signalStrength2.evdo.signalNoiseRatio, signalStrength2.lte.signalStrength, signalStrength2.lte.rsrp, signalStrength2.lte.rsrq, signalStrength2.lte.rssnr, signalStrength2.lte.cqi, tdscdmaRscp_1_2);
    }

    @VisibleForTesting
    public static SignalStrength convertHalSignalStrength_1_2(android.hardware.radio.V1_2.SignalStrength signalStrength) {
        android.hardware.radio.V1_2.SignalStrength signalStrength2 = signalStrength;
        return new SignalStrength(signalStrength2.gsm.signalStrength, signalStrength2.gsm.bitErrorRate, signalStrength2.cdma.dbm, signalStrength2.cdma.ecio, signalStrength2.evdo.dbm, signalStrength2.evdo.ecio, signalStrength2.evdo.signalNoiseRatio, signalStrength2.lte.signalStrength, signalStrength2.lte.rsrp, signalStrength2.lte.rsrq, signalStrength2.lte.rssnr, signalStrength2.lte.cqi, signalStrength2.tdScdma.rscp, signalStrength2.wcdma.base.signalStrength, signalStrength2.wcdma.rscp, -1);
    }

    public void addRequestEx(RILRequest rr) {
        addRequest(rr);
    }

    public void handleRadioProxyExceptionForRREx(String caller, Exception e, RILRequest rr) {
        handleRadioProxyExceptionForRR(rr, caller, e);
    }

    public int getLastRadioTech() {
        return this.mLastRadioTech;
    }

    public Context getContext() {
        return this.mContext;
    }

    public void hvCheckCard(Message result) {
        if (getRadioProxy(result) != null) {
            RILRequest rr = obtainRequest(2111, result, this.mRILDefaultWorkSource);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(rr.serialString());
            stringBuilder.append("> ");
            stringBuilder.append(requestToString(rr.mRequest));
            riljLog(stringBuilder.toString());
        }
    }

    public void notifyUnsolOemHookResponse(byte[] ret) {
        ByteBuffer oemHookResponse = ByteBuffer.wrap(ret);
        oemHookResponse.order(ByteOrder.nativeOrder());
        if (isQcUnsolOemHookResp(oemHookResponse)) {
            Rlog.d(RILJ_LOG_TAG, "OEM ID check Passed");
            processUnsolOemhookResponse(oemHookResponse, ret);
        } else if (this.mUnsolOemHookRawRegistrant != null) {
            Rlog.d(RILJ_LOG_TAG, "External OEM message, to be notified");
            this.mUnsolOemHookRawRegistrant.notifyRegistrant(new AsyncResult(null, ret, null));
        }
    }

    private boolean isQcUnsolOemHookResp(ByteBuffer oemHookResponse) {
        if (oemHookResponse.capacity() < this.mHeaderSize) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("RIL_UNSOL_OEM_HOOK_RAW data size is ");
            stringBuilder.append(oemHookResponse.capacity());
            riljLog(stringBuilder.toString());
            return false;
        }
        byte[] oemIdBytes = new byte[OEM_IDENTIFIER.length()];
        oemHookResponse.get(oemIdBytes);
        String oemIdString = new String(oemIdBytes, Charset.forName("UTF-8"));
        StringBuilder stringBuilder2 = new StringBuilder();
        stringBuilder2.append("Oem ID in RIL_UNSOL_OEM_HOOK_RAW is ");
        stringBuilder2.append(oemIdString);
        riljLog(stringBuilder2.toString());
        if (OEM_IDENTIFIER.equals(oemIdString)) {
            return true;
        }
        return false;
    }

    private void processUnsolOemhookResponse(ByteBuffer oemHookResponse, byte[] ret) {
        int responseId = oemHookResponse.getInt();
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Response ID in RIL_UNSOL_OEM_HOOK_RAW is ");
        stringBuilder.append(responseId);
        riljLog(stringBuilder.toString());
        int responseSize = oemHookResponse.getInt();
        if (responseSize < 0) {
            stringBuilder = new StringBuilder();
            stringBuilder.append("Response Size is Invalid ");
            stringBuilder.append(responseSize);
            riljLog(stringBuilder.toString());
            return;
        }
        byte[] responseData = new byte[responseSize];
        StringBuilder stringBuilder2;
        if (oemHookResponse.remaining() == responseSize) {
            oemHookResponse.get(responseData, 0, responseSize);
            switch (responseId) {
                case 525308:
                    stringBuilder2 = new StringBuilder();
                    stringBuilder2.append("QCRIL_EVT_HOOK_UNSOL_MODEM_CAPABILITY = mPhoneId");
                    stringBuilder2.append(this.mPhoneId);
                    riljLog(stringBuilder2.toString());
                    notifyModemCap(responseData, this.mPhoneId);
                    break;
                case 525341:
                    stringBuilder2 = new StringBuilder();
                    stringBuilder2.append("OEMHOOK_EVT_HOOK_UNSOL_RAT_RAC_CHANGED = mPhoneId");
                    stringBuilder2.append(this.mPhoneId);
                    riljLog(stringBuilder2.toString());
                    sendRacChangeBroadcast(responseData);
                    break;
                case 598029:
                    notifyVpStatus(responseData);
                    break;
                case 598032:
                    stringBuilder2 = new StringBuilder();
                    stringBuilder2.append("QCRIL_EVT_HOOK_UNSOL_HW_MODEM_GENERIC_IND = mPhoneId");
                    stringBuilder2.append(this.mPhoneId);
                    riljLog(stringBuilder2.toString());
                    notifyAntOrMaxTxPowerInfo(responseData);
                    break;
                case 598035:
                    stringBuilder2 = new StringBuilder();
                    stringBuilder2.append("QCRIL_EVT_HOOK_UNSOL_HW_RF_BAND_INFO = mPhoneId");
                    stringBuilder2.append(this.mPhoneId);
                    riljLog(stringBuilder2.toString());
                    notifyBandClassInfo(responseData);
                    break;
                case 598044:
                    stringBuilder2 = new StringBuilder();
                    stringBuilder2.append("received QCRILHOOK_UNSOL_HW_REPORT_BUFFER buffer is :");
                    stringBuilder2.append(IccUtils.bytesToHexString(responseData));
                    riljLog(stringBuilder2.toString());
                    processHWBufferUnsolicited(responseData);
                    break;
                case 598046:
                    if (this.mUnsolOemHookRawRegistrant != null) {
                        Rlog.d(RILJ_LOG_TAG, "External OEM booster message, to be notified");
                        this.mUnsolOemHookRawRegistrant.notifyRegistrant(new AsyncResult(null, ret, null));
                        break;
                    }
                    break;
            }
            return;
        }
        stringBuilder2 = new StringBuilder();
        stringBuilder2.append("Response Size(");
        stringBuilder2.append(responseSize);
        stringBuilder2.append(") doesnot match remaining bytes(");
        stringBuilder2.append(oemHookResponse.remaining());
        stringBuilder2.append(") in the buffer. So, don't process further");
        riljLog(stringBuilder2.toString());
    }

    protected void notifyVpStatus(byte[] data) {
        int len = data.length;
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("notifyVpStatus: len = ");
        stringBuilder.append(len);
        riljLog(stringBuilder.toString());
        if (1 == len) {
            this.mReportVpStatusRegistrants.notifyRegistrants(new AsyncResult(null, data, null));
        }
    }

    protected void notifyModemCap(byte[] data, Integer phoneId) {
        this.mModemCapRegistrants.notifyRegistrants(new AsyncResult(null, new UnsolOemHookBuffer(phoneId.intValue(), data), null));
        String str = RILJ_LOG_TAG;
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("MODEM_CAPABILITY on phone=");
        stringBuilder.append(phoneId);
        stringBuilder.append(" notified to registrants");
        Rlog.d(str, stringBuilder.toString());
    }

    private boolean convertToLteEnableProp(int networkType) {
        if (networkType == 8 || networkType == 9 || networkType == 30 || networkType == 31 || networkType == 61 || networkType == 10 || networkType == 11 || networkType == 12) {
            return true;
        }
        return false;
    }

    private void custSetModemProperties() {
        int isSlotsSwitched = System.getInt(this.mContext.getContentResolver(), "switch_dual_card_slots", 0);
        if ((isSlotsSwitched != 1 || (this.mPhoneId != null && this.mPhoneId.intValue() != 0)) && (isSlotsSwitched != 0 || this.mPhoneId.intValue() != 1)) {
            boolean lte_enabled = convertToLteEnableProp(this.mPreferredNetworkType);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("mPhoneId = ");
            stringBuilder.append(this.mPhoneId);
            stringBuilder.append(", setprop lte_enabled = ");
            stringBuilder.append(lte_enabled);
            riljLog(stringBuilder.toString());
            SystemProperties.set(PROP_LTE_ENABLED, String.valueOf(lte_enabled));
        }
    }

    private RadioResponseInfo convertToOriginalRadioResponseInfo(Object responseInfo) {
        RadioResponseInfo radioResponseInfo = new RadioResponseInfo();
        if (responseInfo != null) {
            if (responseInfo instanceof vendor.huawei.hardware.hisiradio.V1_0.RadioResponseInfo) {
                radioResponseInfo.serial = ((vendor.huawei.hardware.hisiradio.V1_0.RadioResponseInfo) responseInfo).serial;
                radioResponseInfo.error = ((vendor.huawei.hardware.hisiradio.V1_0.RadioResponseInfo) responseInfo).error;
                radioResponseInfo.type = ((vendor.huawei.hardware.hisiradio.V1_0.RadioResponseInfo) responseInfo).type;
            } else if (responseInfo instanceof vendor.huawei.hardware.radio.V2_0.RadioResponseInfo) {
                radioResponseInfo.serial = ((vendor.huawei.hardware.radio.V2_0.RadioResponseInfo) responseInfo).serial;
                radioResponseInfo.error = ((vendor.huawei.hardware.radio.V2_0.RadioResponseInfo) responseInfo).error;
                radioResponseInfo.type = ((vendor.huawei.hardware.radio.V2_0.RadioResponseInfo) responseInfo).type;
            }
        }
        return radioResponseInfo;
    }

    public RILRequest processResponseEx(Object responseInfo) {
        return processResponse(convertToOriginalRadioResponseInfo(responseInfo));
    }

    public void processResponseDoneEx(RILRequest rr, Object responseInfo, Object ret) {
        processResponseDone(rr, convertToOriginalRadioResponseInfo(responseInfo), ret);
    }
}
