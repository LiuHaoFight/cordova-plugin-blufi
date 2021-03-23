package com.siemens.blufi;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.LOG;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;
import java.util.List;

import android.util.Log;

import android.app.Activity;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.pm.PackageManager;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.text.TextUtils;

import com.siemens.blufi.BlufiCallback;
import com.siemens.blufi.BlufiClient;
import com.siemens.blufi.BlufiConstants;
import com.siemens.blufi.params.BlufiConfigureParams;
import com.siemens.blufi.params.BlufiParameter;
import com.siemens.blufi.response.BlufiScanResult;
import com.siemens.blufi.response.BlufiStatusResponse;
import com.siemens.blufi.response.BlufiVersionResponse;

/**
 * This class echoes a string called from JavaScript.
 */
public class Blufi extends CordovaPlugin {

    private static final String TAG = Blufi.class.getSimpleName();
    public BluetoothAdapter bluetoothAdapter;
    public BluetoothLeScanner bluetoothLeScanner;
    private BlufiClient mBlufiClient;
    private CallbackContext mConnectCallback;
    private CallbackContext mConfigureCallback;
    private CallbackContext mStatusCallback;
    private CallbackContext mDisconnectCallback;
    private CallbackContext mCustomCallback;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        LOG.i(TAG, "action = %s", action);

        if (action.equals("connect")) {
            String mac = args.getString(0);
            this.connect(mac, callbackContext);
            return true;
        } else if (action.equals("configure")) {
            this.configure(args, callbackContext);
            return true;
        } else if (action.equals("disconnect")) {
            this.disconnect(callbackContext);
            return true;
        } else if (action.equals("request_device_status")) {
            this.requestDeviceStatus(callbackContext);
            return true;
        } else if (action.equals("custom_data")) {
            this.postCustomData(args, callbackContext);
            return true;
        }
        return false;
    }

    private void requestDeviceStatus(CallbackContext callbackContext) {
        Blufi.this.mStatusCallback = callbackContext;
        if (mBlufiClient == null) {
            callbackContext.error("no_device");
            return;
        }
        mBlufiClient.requestDeviceStatus();
    }

    private void disconnect(CallbackContext callbackContext) {
        Blufi.this.mDisconnectCallback = callbackContext;
        if (mBlufiClient == null) {
            callbackContext.error("no_device");
            return;
        }
        mBlufiClient.requestCloseConnection();
    }

    private void configure(JSONArray args, CallbackContext callbackContext) {
        Blufi.this.mConfigureCallback = callbackContext;
        if (mBlufiClient == null) {
            callbackContext.error("no_device");
            return;
        }
        BlufiConfigureParams params = this.getConfigureParams(args, callbackContext);
        if (params != null) {
            mBlufiClient.configure(params);
        }
    }

    public void postCustomData(JSONArray args, CallbackContext callbackContext){
        Blufi.this.mCustomCallback = callbackContext;

        if (mBlufiClient == null) {
            callbackContext.error("no_device");
            return;
        }
        try {
            String cmmd = args.getString(0);
            mBlufiClient.postCustomData(cmmd.getBytes());
        } catch (Exception e) {
            e.printStackTrace();
            callbackContext.error("param_error");
        }
        
    }

    private BlufiConfigureParams getConfigureParams(JSONArray args, CallbackContext callbackContext) {
        BlufiConfigureParams params = new BlufiConfigureParams();
        try {
            int deviceMode = args.getInt(0);
            params.setOpMode(deviceMode);
            switch (deviceMode) {
                case BlufiParameter.OP_MODE_NULL:
                    return params;
                case BlufiParameter.OP_MODE_STA:
                    return buildStaParams(params, args);
                case BlufiParameter.OP_MODE_SOFTAP:
                    return buildSoftAPParams(params, args);
                case BlufiParameter.OP_MODE_STASOFTAP:
                    params = buildStaParams(params, args);
                    return buildSoftAPParams(params, args);
                default:
                    if (Blufi.this.mConfigureCallback != null) {
                        Blufi.this.mConfigureCallback.error("mode_error");
                    }
                    return null;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private BlufiConfigureParams buildStaParams(BlufiConfigureParams params, JSONArray args) {
        try {
            String ssid = args.getString(1);
            String password = args.getString(2);
            if (TextUtils.isEmpty(ssid)) {
                if (Blufi.this.mConfigureCallback != null) {
                    Blufi.this.mConfigureCallback.error("ssid_empty");
                }
                return null;
            }
            params.setStaSSIDBytes(ssid.getBytes());
            params.setStaPassword(password);
        } catch (Exception e) {
            if (Blufi.this.mConfigureCallback != null) {
                Blufi.this.mConfigureCallback.error("params invilid");
            }
            return null;
        }
        return params;
    }

    public BlufiConfigureParams buildSoftAPParams(BlufiConfigureParams params, JSONArray args) {
        try {
            String ssid = args.getString(1);
            String password = args.getString(2);
            int channel = args.getInt(3);
            int maxConnection = args.getInt(4);
            int security = args.getInt(5);
            if (TextUtils.isEmpty(ssid)) {
                if (Blufi.this.mConfigureCallback != null) {
                    Blufi.this.mConfigureCallback.error("ssid_empty");
                }
                return null;
            }
            params.setSoftAPSSID(ssid);
            params.setSoftAPPAssword(password);
            params.setSoftAPChannel(channel);
            params.setSoftAPMaxConnection(maxConnection);
            params.setSoftAPSecurity(security);
            switch (security) {
                case BlufiParameter.SOFTAP_SECURITY_OPEN:
                    break;
                case BlufiParameter.SOFTAP_SECURITY_WEP:
                case BlufiParameter.SOFTAP_SECURITY_WPA:
                case BlufiParameter.SOFTAP_SECURITY_WPA2:
                case BlufiParameter.SOFTAP_SECURITY_WPA_WPA2:
                    if (TextUtils.isEmpty(password) || password.length() < 8) {
                        if (Blufi.this.mConfigureCallback != null) {
                            Blufi.this.mConfigureCallback.error("password invilid");
                        }
                        return null;
                    }
                    return params;
            }
            return params;
        } catch (Exception e) {
            if (Blufi.this.mConfigureCallback != null) {
                Blufi.this.mConfigureCallback.error("params invilid");
            }
            return null;
        }
    }


    private void connect(String mac, CallbackContext callbackContext) {
        Blufi.this.mConnectCallback = callbackContext;
        LOG.i(TAG, "---connnect mac: " + mac);
        if (mac != null && mac.length() > 0) {
            if (mBlufiClient != null) {
                mBlufiClient.close();
                mBlufiClient = null;
            }
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(mac);
            if (device != null) {
                mBlufiClient = new BlufiClient(cordova.getActivity(), device);
                mBlufiClient.setGattCallback(new GattCallback());
                mBlufiClient.setBlufiCallback(new BlufiCallbackMain());
                mBlufiClient.connect();
            } else {
                callbackContext.error("no device found with the mac address: " + mac);
            }
        } else {
            callbackContext.error("mac_invilid");
        }
    }

    private class GattCallback extends BluetoothGattCallback {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String devAddr = gatt.getDevice().getAddress();
            LOG.i(TAG, String.format(Locale.ENGLISH, "onConnectionStateChange addr=%s, status=%d, newState=%d",
                    devAddr, status, newState));
            if (status == BluetoothGatt.GATT_SUCCESS) {
                switch (newState) {
                    case BluetoothProfile.STATE_CONNECTED:
                        if (Blufi.this.mDisconnectCallback != null) {
                            Blufi.this.mDisconnectCallback.error("conncted");
                            Blufi.this.mDisconnectCallback = null;
                        }
                        break;
                    case BluetoothProfile.STATE_DISCONNECTED:
                        gatt.close();
                        if (Blufi.this.mConnectCallback != null) {
                            Blufi.this.mConnectCallback.error("disconnect");
                        }
                        if (Blufi.this.mDisconnectCallback != null) {
                            Blufi.this.mDisconnectCallback.success();
                            Blufi.this.mDisconnectCallback = null;
                        }
                        break;
                }
            } else {
                gatt.close();
                if (Blufi.this.mConnectCallback != null) {
                    Blufi.this.mConnectCallback.error("disconnect");
                }
                if (Blufi.this.mDisconnectCallback != null) {
                    Blufi.this.mDisconnectCallback.success();
                    Blufi.this.mDisconnectCallback = null;
                }
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            LOG.i(TAG, String.format(Locale.ENGLISH, "onMtuChanged status=%d, mtu=%d", status, mtu));
            if (status != BluetoothGatt.GATT_SUCCESS) {
                mBlufiClient.setPostPackageLengthLimit(20);
            }
            if (Blufi.this.mConnectCallback != null) {
                Blufi.this.mConnectCallback.success();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            LOG.i(TAG, String.format(Locale.ENGLISH, "onServicesDiscovered status=%d", status));
            if (status != BluetoothGatt.GATT_SUCCESS) {
                gatt.disconnect();
                if (Blufi.this.mConnectCallback != null) {
                    Blufi.this.mConnectCallback.error("server_not_found");
                }
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            LOG.i(TAG, String.format(Locale.ENGLISH, "onDescriptorWrite status=%d", status));
            if (descriptor.getUuid().equals(BlufiParameter.UUID_NOTIFICATION_DESCRIPTOR) &&
                    descriptor.getCharacteristic().getUuid().equals(BlufiParameter.UUID_NOTIFICATION_CHARACTERISTIC) &&
                    status != BluetoothGatt.GATT_SUCCESS) {
                if (Blufi.this.mConnectCallback != null) {
                    Blufi.this.mConnectCallback.error("descriptor_error");
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            LOG.i(TAG, String.format(Locale.ENGLISH, "onCharacteristicWrite status=%d", status));
            if (status != BluetoothGatt.GATT_SUCCESS) {
                gatt.disconnect();
            }
        }
    }

    private class BlufiCallbackMain extends BlufiCallback {
        @Override
        public void onGattPrepared(BlufiClient client, BluetoothGatt gatt, BluetoothGattService service,
                                   BluetoothGattCharacteristic writeChar, BluetoothGattCharacteristic notifyChar) {
            if (service == null) {
                LOG.w(TAG, "Discover service failed");
                gatt.disconnect();
                return;
            }
            if (writeChar == null) {
                LOG.w(TAG, "Get write characteristic failed");
                gatt.disconnect();
                return;
            }
            if (notifyChar == null) {
                LOG.w(TAG, "Get notification characteristic failed");
                gatt.disconnect();
                return;
            }

            LOG.i(TAG, "Discover service and characteristics success");

            int mtu = BlufiConstants.DEFAULT_MTU_LENGTH;
            LOG.i(TAG, "Request MTU " + mtu);
            boolean requestMtu = gatt.requestMtu(mtu);
            if (!requestMtu) {
                LOG.w(TAG, "Request mtu failed");
            }
        }

        @Override
        public void onNegotiateSecurityResult(BlufiClient client, int status) {
            if (status == BlufiCallback.STATUS_SUCCESS) {
                LOG.i(TAG, "Negotiate security complete");
            } else {
                LOG.i(TAG, "Negotiate security failed， code=" + status);
            }
        }

        @Override
        public void onPostConfigureParams(BlufiClient client, int status) {
            if (status == BlufiCallback.STATUS_SUCCESS) {
                LOG.i(TAG, "Post configure params complete");
                if (Blufi.this.mConfigureCallback != null) {
                    Blufi.this.mConfigureCallback.success();
                }
            } else {
                LOG.i(TAG, "Post configure params failed, code=" + status);
                if (Blufi.this.mConfigureCallback != null) {
                    Blufi.this.mConfigureCallback.error("write_failed");
                }
            }
        }

        @Override
        public void onDeviceStatusResponse(BlufiClient client, int status, BlufiStatusResponse response) {
            if (status == BlufiCallback.STATUS_SUCCESS) {
                LOG.i(TAG, String.format("Receive device status response:\n%s", response.toString()));
                if(Blufi.this.mStatusCallback!=null) {
                    JSONObject result = new JSONObject();
                    try {
                        result.put("connected", response.isStaConnectWifi());
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    Blufi.this.mStatusCallback.success(result);
                }
            } else {
                LOG.i(TAG, "Device status response error, code=" + status);
                if(Blufi.this.mStatusCallback!=null) {
                    Blufi.this.mStatusCallback.error("failed");
                }
            }
        }

        @Override
        public void onDeviceScanResult(BlufiClient client, int status, List<BlufiScanResult> results) {
            if (status == BlufiCallback.STATUS_SUCCESS) {
                StringBuilder msg = new StringBuilder();
                msg.append("Receive device scan result:\n");
                for (BlufiScanResult scanResult : results) {
                    msg.append(scanResult.toString()).append("\n");
                }
                LOG.i(TAG, msg.toString());
            } else {
                LOG.e(TAG, "Device scan result error, code=" + status);
            }
        }

        @Override
        public void onDeviceVersionResponse(BlufiClient client, int status, BlufiVersionResponse response) {
            if (status == BlufiCallback.STATUS_SUCCESS) {
                LOG.i(TAG, String.format("Receive device version: %s", response.getVersionString()));
            } else {
                LOG.i(TAG, "Device version error, code=" + status);
            }
        }

        @Override
        public void onPostCustomDataResult(BlufiClient client, int status, byte[] data) {
            String dataStr = new String(data);
            String format = "Post data %s %s";
            if (status == BlufiCallback.STATUS_SUCCESS) {
                LOG.i(TAG, String.format(format, dataStr, "complete"));
            } else {
                LOG.e(TAG, String.format(format, dataStr, "failed"));
            }
        }

        @Override
        public void onReceiveCustomData(BlufiClient client, int status, byte[] data) {
            if (status == BlufiCallback.STATUS_SUCCESS) {
                String customStr = new String(data);
                LOG.i(TAG, String.format("Receive custom data:\n%s", customStr));
                if (Blufi.this.mCustomCallback != null) {
                    Blufi.this.mCustomCallback.success(customStr);
                }
            } else {
                LOG.i(TAG, "Receive custom data error, code=" + status);
                if (Blufi.this.mCustomCallback != null) {
                    Blufi.this.mCustomCallback.error(status);
                }
            }
        }

        @Override
        public void onError(BlufiClient client, int errCode) {
            LOG.e(TAG, String.format(Locale.ENGLISH, "Receive error code %d", errCode));
        }
    }
}
