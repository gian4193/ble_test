package com.example.ble_test;

import android.content.Context;
import android.util.Log;

import com.inuker.bluetooth.library.BluetoothClient;
import com.inuker.bluetooth.library.beacon.Beacon;
import com.inuker.bluetooth.library.connect.listener.BluetoothStateListener;
import com.inuker.bluetooth.library.connect.response.BleNotifyResponse;
import com.inuker.bluetooth.library.receiver.listener.BluetoothBondListener;
import com.inuker.bluetooth.library.search.SearchRequest;
import com.inuker.bluetooth.library.search.SearchResult;
import com.inuker.bluetooth.library.search.response.SearchResponse;
import com.inuker.bluetooth.library.utils.BluetoothLog;

import java.util.UUID;

import static com.example.ble_test.BleUuidTransFormer.convertFromInteger;
import static com.inuker.bluetooth.library.Code.REQUEST_SUCCESS;

public abstract  class BleController {
    public abstract void setStatus(String status);

    public abstract void setBloodPressureMeasuredValuesToUi();

    public abstract void setBloodSugarMeasureValueToUi(int sugar);

    public abstract void uploadBloodPressureToCloud();

    public abstract void uploadBloodSugarToCloud(int sugar);

    private static final String TAG = "BleController";
    public static final String STATUS_UNSTART = "未啟動，請先按下\"開始測量\"按鈕，再進行量測";
    public static final String STATUS_BLUETOOTH_OPENING = "藍牙正在啟動中，請稍候";
    public static final String STATUS_BLUETOOTH_NOT_YET = "藍牙未啟動，請先手動開啟藍牙，再進行量測";
    public static final String STATUS_BLUETOOTH_OK = "藍牙已準備，請開始量測";
    public static final String STATUS_CONNECTING = "正在連接血糖血壓器，請不要關閉此App與藍牙";
    public static final String STATUS_CONNECTED = "已連接到血糖血壓器，正在傳送量測資料中";
    public static final String STATUS_NO_RETRY = "沒有找到血糖血壓器，請確認你有沒有開啟血糖血壓器";
    public static final String STATUS_FINISHED = "已接收到您的量測資料，感謝您的使用";

    public static final UUID BLOOD_PRESSURE_SERVICE = convertFromInteger(0x1809); //Health Thermometer
    public static final UUID BLOOD_PRESSURE_CHAR = convertFromInteger(0x2A1C);    //Temperature Measurement
    public static final UUID BLOOD_SUGAR_SERVICE = convertFromInteger(0x1818);
    public static final UUID BLOOD_SUGAR_MEASURE_CHAR = convertFromInteger(0x2a18);
    public static final UUID BLOOD_SUGAR_FEATURE_CHAR = convertFromInteger(0x2a51);
    public static final UUID BLOOD_SUGAR_MEASURE_CONTEXT_CHAR = convertFromInteger(0x2a34);

    public static final UUID BLOOD_SUGAR_CONTINUOUS_SERVICE = convertFromInteger(0x181f);
    public static final UUID BLOOD_SUGAR_CONTINUOUS_MEASUREMENT_CHAR = convertFromInteger(0x2aa7);
    public static final UUID BLOOD_SUGAR_CONTINUOUS_RUNTIME_CHAR = convertFromInteger(0x2aab);
    public static final UUID BLOOD_SUGAR_CONTINUOUS_STARTTIME_CHAR = convertFromInteger(0x2aaa);
    public static final UUID BLOOD_SUGAR_CONTINUOUS_OPS_CONTROL_CHAR = convertFromInteger(0x2aac);
    public static final UUID BLOOD_SUGAR_CONTINUOUS_STATUS_CHAR = convertFromInteger(0x2aa9);

    private static final int SCAN_INTERVAL = 5;     // 5 secs
    private static final int UNIT_S_TO_MS = 1000;
    private static final int MAX_SCAN_TOLERANT = 7;     // Retry 7 times when we cannot find the target device in BLE

    private BluetoothClient mBleClient;
    private Context mContext;
    private String mDeviceAddress;
    private String mTargetDeviceAddress;
    private boolean isMeasured = false;
    private int mScanTolerant = 0;
    private boolean isSugarMeasuring;


    public void setMeasured(boolean measured) {
        isMeasured = measured;
    }

    public void start(){
        if (mBleClient == null) return;

        Log.d(TAG, "start");
        if (mBleClient.isBluetoothOpened()){
            startScaning();
        }else{
            mBleClient.openBluetooth();
        }
    }

    public BleController(Context context, String targetMac, boolean isSugarMeasuring){
        mContext = context;
        mTargetDeviceAddress = targetMac;
        this.isSugarMeasuring = isSugarMeasuring;
    }

    public void init(){
        if (mContext != null){
            Log.d(TAG, "init success");
            mBleClient = new BluetoothClient(mContext);
            mBleClient.registerBluetoothStateListener(mBluetoothStateListener);
            mBleClient.registerBluetoothBondListener(mBluetoothBondListener);
        }
    }

    private final BluetoothStateListener mBluetoothStateListener = new BluetoothStateListener() {
        @Override
        public void onBluetoothStateChanged(boolean bluetoothOpened) {
            Log.d(TAG, "onBluetoothStateChanged: start? " + bluetoothOpened);

            if (isMeasured){
                setStatus(STATUS_FINISHED);
                return;
            }

            if (bluetoothOpened){
                startScaning();
            }else{
                setStatus(STATUS_BLUETOOTH_NOT_YET);
            }
        }
    };

    /*
     * Listen to bluetooth connection state
     */
    private final BluetoothBondListener mBluetoothBondListener = new BluetoothBondListener() {
        @Override
        public void onBondStateChanged(String mac, int bondState) {
            Log.d(TAG, "onBondStateChanged:\nmac: " + mac + " / bondstate: " + bondState);
        }
    };


    private void startScaning(){
       // setStatus(STATUS_BLUETOOTH_OK);

        // start discovering
        scan();
    }

    public void scan() {
        if (mBleClient == null) return;

        if (mBleClient.isBluetoothOpened()){
            SearchRequest request = new SearchRequest.Builder()
                    .searchBluetoothLeDevice(SCAN_INTERVAL * UNIT_S_TO_MS)
                    .searchBluetoothClassicDevice(SCAN_INTERVAL * UNIT_S_TO_MS)
                    .build();

            // start scanning
            mBleClient.search(request, mSearchResponse);
        }else{
            Log.d(TAG, "BLE is not opened");
        }
    }


    private SearchResponse mSearchResponse = new SearchResponse() {
        @Override
        public void onSearchStarted() {
            Log.d(TAG, "onSearchStarted");
        }

        @Override
        public void onDeviceFounded(SearchResult device) {      // List all founded devices' info
            if (isMeasured) return;

            Log.d(TAG, "onDeviceFounded");
            Beacon beacon = new Beacon(device.scanRecord);
            BluetoothLog.v(String.format("beacon for %s %s\n%s", device.getName(),  device.getAddress(), beacon.toString()));

            if (device.getAddress().equals(mTargetDeviceAddress)){      // Assign global mac address if FORA is founded
                Log.d(TAG, "Contains FORA D40b");
                mDeviceAddress = device.getAddress();
                connect();
            }
        }

        @Override
        public void onSearchStopped() {
            Log.d(TAG, "onSearchStopped");
            if (mScanTolerant < MAX_SCAN_TOLERANT){
                scan();
            }else{
                setStatus(STATUS_NO_RETRY);
            }
        }

        @Override
        public void onSearchCanceled() {
            Log.d(TAG, "onSearchCanceled");
        }
    };

    public void connect(){
        if (mBleClient == null) return;
        Log.d(TAG, STATUS_CONNECTING);
        //setStatus(STATUS_CONNECTING);

        mBleClient.connect(mDeviceAddress, (code, data) -> {
            // When connect to target device successfully
            if (code == REQUEST_SUCCESS){
                Log.d(TAG, "connect success: profile: " + data.toString());
               // setStatus(STATUS_CONNECTED);

                // Get blood pressure data
                mBleClient.indicate(mDeviceAddress, BLOOD_PRESSURE_SERVICE,
                        BLOOD_PRESSURE_CHAR, mBloodPressureMeasureNotifyResponse);

                /*
                 * Get all Characteristics, but doesn't contain blood pressure data
                 */
//                List<BleGattCharacter> list = data.getService(BLOOD_PRESSURE_SERVICE).getCharacters();
//                for (BleGattCharacter bleChar : list){
//                    Log.d(TAG, "BleChars: " + bleChar.toString());
//                }
            }else{
                Log.d(TAG, "connect failed");
            }
        });
    }


    private int checkByteIntConversion(byte value){
        return value & 0xFF;
    }


    private BleNotifyResponse mBloodPressureMeasureNotifyResponse = new BleNotifyResponse() {
        @Override
        public void onNotify(UUID service, UUID character, byte[] value) {
            if (isSugarMeasuring){
                int sugar = checkByteIntConversion(value[1]);
                Log.d(TAG, "onNotify, service: " + service + "/ char: " + character + "/ value: " + value);
                Log.d(TAG, "sugar: " + sugar);

                setBloodSugarMeasureValueToUi(sugar);
                uploadBloodSugarToCloud(sugar);
            }else{

//                int systolic = checkByteIntConversion(value[1]);
//                int diastolic = checkByteIntConversion(value[3]);
//                int pulse = checkByteIntConversion(value[14]);
//
//                Log.d(TAG, "onNotify,service: " + service + "/ char: " + character + "/ value: " + value);
//                Log.d(TAG, "Systolic: " + systolic + "/ Diastolic: " + diastolic + "/ pulse rate: " + pulse);

                  Log.d(TAG,String.valueOf(value.length));
                  for(int i = 0 ; i<value.length ; i++){
                      Log.d(TAG,String.valueOf(checkByteIntConversion(value[i])));
                  }
//            setStatus("收縮壓: " + systolic + " mmHg | 舒張壓: " + diastolic + " mmHg | 心律: " + pulse + " 下/分鐘");

                setBloodPressureMeasuredValuesToUi();
                uploadBloodPressureToCloud();
            }

            disconnect();
            closeBluetooth();
            isMeasured = true;
        }

        @Override
        public void onResponse(int code) {
            Log.e(TAG, "onResponse code: " + code);
        }
    };

    public void disconnect(){
        if (mBleClient == null) return;

        mBleClient.disconnect(mDeviceAddress);
    }

    public void closeBluetooth(){
        if (mBleClient == null) return;

        mBleClient.closeBluetooth();
    }





}
