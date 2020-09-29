package com.example.ble_test;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "BLETEST";
    public static final String FORA_D40_MAC = "C0:26:DA:01:EA:69";
    private BleController mBleController;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);



            mBleController = new BleController(this, FORA_D40_MAC, false) {
                @Override
                public void setStatus(String status) {
                    //this.setStatus(status);
                }

                @Override
                public void setBloodPressureMeasuredValuesToUi() {
                    Log.e(TAG, "setBloodPressureMeasuredValuesToUi");


                    // speaking warning sentence when pressure value is abnormal

                }

                @Override
                public void setBloodSugarMeasureValueToUi(int sugar) {
                    // Do nothing, because this is blood pressure part.
                }

                @Override
                public void uploadBloodPressureToCloud() {
                    Log.e(TAG, "upload");
                }

                @Override
                public void uploadBloodSugarToCloud(int sugar) {
                    // Do nothing, because this is blood pressure part.
                }
            };
            mBleController.init();
            mBleController.setMeasured(false);
            mBleController.start();


    }


}


