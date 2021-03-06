package com.android.server.fingerprint;

import android.content.Context;
import android.hardware.fingerprint.Fingerprint;
import android.hardware.fingerprint.IFingerprintServiceReceiver;
import android.os.Bundle;
import android.os.Parcel;
import android.os.RemoteException;
import com.android.server.SystemService;
import java.util.Collections;
import java.util.List;

public abstract class AbsFingerprintService extends SystemService {
    public AbsFingerprintService(Context context) {
        super(context);
    }

    public void updateFingerprints(int userId) {
    }

    public boolean shouldAuthBothSpaceFingerprints(String opPackageName, int flags) {
        return true;
    }

    public int removeUserData(int groupId, byte[] path) {
        return 0;
    }

    public boolean onHwTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        return false;
    }

    public boolean checkPrivacySpaceEnroll(int userId, int currentUserId) {
        return false;
    }

    public boolean checkNeedPowerpush() {
        return false;
    }

    protected void stopPickupTrunOff() {
    }

    public void setFingerprintMaskView(Bundle bundle) {
    }

    public void showFingerprintView() {
    }

    public void notifyAuthenticationStarted(String pkgName, IFingerprintServiceReceiver receiver, int flag, int userID) {
    }

    public void notifyAuthenticationCanceled(String pkgName) {
    }

    public void notifyFingerDown(int type) {
    }

    public void notifyFingerCalibrarion(int value) {
    }

    public void notifyAuthenticationFinished(String opName, int type, int failtime) {
    }

    public void notifyEnrollmentStarted(int flags) {
    }

    public void notifyEnrollmentCanceled() {
    }

    public void notifyCaptureFinished(int type) {
    }

    public void notifyEnrollingFingerUp() {
    }

    protected void setKidsFingerprint(int userID, boolean isKeygusrd) {
    }

    protected int sendCommandToHal(int command) {
        return 0;
    }

    protected boolean canUseUdFingerprint(String opPackageName) {
        return false;
    }

    protected boolean isSupportDualFingerprint() {
        return false;
    }

    protected List<Fingerprint> getEnrolledFingerprintsEx(String opPackageName, int targetDevice, int userId) {
        return Collections.emptyList();
    }

    protected boolean isHardwareDetectedEx(String opPackageName, int targetDevice) {
        return true;
    }
}
