package com.myriadmobile.bletransfer;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import butterknife.ButterKnife;
import butterknife.InjectView;


public class CentralActivity extends Activity {

    // Bluetooth Variables
    BluetoothManager bluetoothManager;
    BluetoothAdapter mBluetoothAdapter;
    BluetoothGatt gattClient;
    BluetoothDevice gattDevice;

    // Holds the incoming message chunks
    String dataBuffer;

    @InjectView(R.id.tvData)
    TextView tvData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_central);
        ButterKnife.inject(this);


        // Get the bluetooth manager and adapter
        bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Ensure they have Bluetooth turned on
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Log.d("auto", "Bluetooth is not turned on");
            Toast.makeText(this, "Bluetooth is not turned on", Toast.LENGTH_SHORT).show();
            return;
        }

        // Start Scanning for our transfer service uuid
        Log.d("auto", "CentralActivity > onCreate() > Start Scanning");
        ScanSettings settings = (new ScanSettings.Builder()).setReportDelay(0).setScanMode(ScanSettings.SCAN_MODE_BALANCED).build();
        ArrayList<ScanFilter> filters = new ArrayList<>();
        filters.add((new ScanFilter.Builder()).setServiceUuid(new ParcelUuid(UUID.fromString(PeripheralActivity.TRANSFER_SERVICE_UUID))).build());
        mBluetoothAdapter.getBluetoothLeScanner().startScan(filters, settings, mScanCallback);


    }

    @Override
    protected void onDestroy() {
        mBluetoothAdapter.getBluetoothLeScanner().stopScan(mScanCallback); // Clean up, stop scanning
        super.onDestroy();

    }

    ScanCallback mScanCallback = new ScanCallback() {

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);

            Log.d("auto", "CentralActivity > onScanResult() > RSSI: " + result.getDevice().getName() + "," + result.getRssi() );
            
            // Is the device close enough?
            if(result.getRssi() > -40 && gattClient == null) {
                // Device is close!

                // Attempt to connect to device
                gattClient = result.getDevice().connectGatt(CentralActivity.this, true, mBluetoothGattCallback);
                if(gattClient.connect()) {

                    // Clear the cache of data
                    dataBuffer = "";
                    gattDevice = result.getDevice();

                    // Stop scanning
                    mBluetoothAdapter.getBluetoothLeScanner().stopScan(mScanCallback);
                } else {
                    Log.d("auto", "CentralActivity > onScanResult() > Unable to connect");
                    gattClient.close();
                    gattClient.disconnect();
                    gattClient = null;
                }


            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            Log.d("auto", "CentralActivity > onBatchScanResults()");
            
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.d("auto", "CentralActivity > onScanFailed() > Error" + errorCode);

        }
    };
    

    BluetoothGattCallback mBluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            Log.d("auto", "CentralActivity > onConnectionStateChange() > State: " + newState);

            if(newState == BluetoothGatt.STATE_CONNECTED) {
                // The device has connected, attempt to discover the services
                if(gatt != null){
                    gatt.discoverServices();
                }

            } else {
                // Disconnected
                // Close and start scanning again
                if(gattClient != null) {
                    gattClient.close();
                    gattClient.disconnect();
                    gattClient = null;
                    // Start Scanning again
                    mBluetoothAdapter.getBluetoothLeScanner().startScan(mScanCallback);
                }
            }

        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            Log.d("auto", "CentralActivity > onCharacteristicChanged()");


            // Is the characteristic the one we care about
            if(characteristic.getUuid().toString().toUpperCase().equals(PeripheralActivity.TRANSFER_CHARACTERISTIC_UUID)) {
                // We care about this characteristic

                // Save data!
                try {
                    dataBuffer += new String(characteristic.getValue(), "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }

                // Is it the end of the message?
                if(dataBuffer.length() >= 3) {
                    String lastThree = dataBuffer.substring(dataBuffer.length() - 3);
                    if(lastThree.equals("EOM")) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                // End of message!  Display
                                tvData.setText(dataBuffer.substring(0, dataBuffer.length()-3));
                            }
                        });
                        // Close gatt server
                        gattClient.disconnect();
                        gattClient.close();
                        gattClient = null;
                        // Start Scanning again
                        mBluetoothAdapter.getBluetoothLeScanner().startScan(mScanCallback);

                    }
                }

            }

        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            Log.d("auto", "CentralActivity > onCharacteristicWrite() > message");
           
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            Log.d("auto", "CentralActivity > onCharacteristicRead() > message");

        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            Log.d("auto", "CentralActivity > onServicesDiscovered()");

            // You found services, now find the characteristic and subscribe
            boolean foundChar = false;

            for(BluetoothGattService service : gatt.getServices()) {
                if(service.getUuid().toString().toUpperCase().equals(PeripheralActivity.TRANSFER_SERVICE_UUID.toUpperCase())) {
                    for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                        if (characteristic.getUuid().toString().toUpperCase().equals(PeripheralActivity.TRANSFER_CHARACTERISTIC_UUID.toUpperCase())) {
                            // We care about this characteristic
                            Log.d("auto", "CentralActivity > onServicesDiscovered() > Found Characteristic");

                            // Request to read this characteristic
                            gatt.readCharacteristic(characteristic);

                            // Set that you want to be notified when this characteristic changes
                            gatt.setCharacteristicNotification(characteristic, true);

                            // Next loop through the Descriptors to update the notification value
                            for(BluetoothGattDescriptor descriptor : characteristic.getDescriptors()) {
                                if(descriptor.getUuid().toString().toUpperCase().equals(PeripheralActivity.TRANSFER_DESCIPRTOR_UUID.toUpperCase())) {
                                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                    gatt.writeDescriptor(descriptor);
                                }
                            }

                            foundChar = true;
                        }
                    }
                }
            }

            // If this device does not have the characteristic, close the gatt server
            if(!foundChar) {
                Log.d("auto", "CentralActivity > onServicesDiscovered() > Not The Right Device");

                // Close and start scanning again
                gattClient.close();
                gattClient.disconnect();
                gattClient = null;
                // Start Scanning again
                mBluetoothAdapter.getBluetoothLeScanner().startScan(mScanCallback);
            }
        }
    };
}
