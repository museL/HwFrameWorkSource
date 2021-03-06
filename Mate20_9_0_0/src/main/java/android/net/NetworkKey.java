package android.net;

import android.annotation.SystemApi;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;
import android.text.TextUtils;
import android.util.Log;
import java.util.Objects;

@SystemApi
public class NetworkKey implements Parcelable {
    public static final Creator<NetworkKey> CREATOR = new Creator<NetworkKey>() {
        public NetworkKey createFromParcel(Parcel in) {
            return new NetworkKey(in, null);
        }

        public NetworkKey[] newArray(int size) {
            return new NetworkKey[size];
        }
    };
    private static final String TAG = "NetworkKey";
    public static final int TYPE_WIFI = 1;
    public final int type;
    public final WifiKey wifiKey;

    /* synthetic */ NetworkKey(Parcel x0, AnonymousClass1 x1) {
        this(x0);
    }

    public static NetworkKey createFromScanResult(ScanResult result) {
        if (!(result == null || result.wifiSsid == null)) {
            String ssid = result.wifiSsid.toString();
            String bssid = result.BSSID;
            if (!(TextUtils.isEmpty(ssid) || ssid.equals("<unknown ssid>") || TextUtils.isEmpty(bssid))) {
                try {
                    return new NetworkKey(new WifiKey(String.format("\"%s\"", new Object[]{ssid}), bssid));
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "Unable to create WifiKey.", e);
                    return null;
                }
            }
        }
        return null;
    }

    public static NetworkKey createFromWifiInfo(WifiInfo wifiInfo) {
        if (wifiInfo != null) {
            String ssid = wifiInfo.getSSID();
            String bssid = wifiInfo.getBSSID();
            if (!(TextUtils.isEmpty(ssid) || ssid.equals("<unknown ssid>") || TextUtils.isEmpty(bssid))) {
                try {
                    return new NetworkKey(new WifiKey(ssid, bssid));
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "Unable to create WifiKey.", e);
                    return null;
                }
            }
        }
        return null;
    }

    public NetworkKey(WifiKey wifiKey) {
        this.type = 1;
        this.wifiKey = wifiKey;
    }

    private NetworkKey(Parcel in) {
        this.type = in.readInt();
        if (this.type == 1) {
            this.wifiKey = (WifiKey) WifiKey.CREATOR.createFromParcel(in);
            return;
        }
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Parcel has unknown type: ");
        stringBuilder.append(this.type);
        throw new IllegalArgumentException(stringBuilder.toString());
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(this.type);
        if (this.type == 1) {
            this.wifiKey.writeToParcel(out, flags);
            return;
        }
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("NetworkKey has unknown type ");
        stringBuilder.append(this.type);
        throw new IllegalStateException(stringBuilder.toString());
    }

    public boolean equals(Object o) {
        boolean z = true;
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NetworkKey that = (NetworkKey) o;
        if (!(this.type == that.type && Objects.equals(this.wifiKey, that.wifiKey))) {
            z = false;
        }
        return z;
    }

    public int hashCode() {
        return Objects.hash(new Object[]{Integer.valueOf(this.type), this.wifiKey});
    }

    public String toString() {
        if (this.type != 1) {
            return "InvalidKey";
        }
        return this.wifiKey.toString();
    }
}
