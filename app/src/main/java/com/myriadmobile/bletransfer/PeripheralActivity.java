package com.myriadmobile.bletransfer;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.UUID;

import butterknife.ButterKnife;
import butterknife.InjectView;


public class PeripheralActivity extends Activity implements CompoundButton.OnCheckedChangeListener {


    // UUIDS
    public static String TRANSFER_SERVICE_UUID = "E20A39F4-73F5-4BC4-A12F-17D1AD07A961";
    public static String TRANSFER_CHARACTERISTIC_UUID = "08590F7E-DB05-467E-8757-72F6FAEB13D4";
    public static String TRANSFER_DESCIPRTOR_UUID = "00002902-0000-1000-8000-00805F9B34FB";

    // Bluetooth Variables
    BluetoothGattServer gattServer;
    BluetoothDevice btClient;
    BluetoothGattService dataService;
    BluetoothGattCharacteristic dataCharacteristic;
    BluetoothManager bluetoothManager;
    BluetoothAdapter mBluetoothAdapter;

    // Data To Send
    ArrayList<byte[]> dataToSend;
    int dataToSendIndex;

    // UI
    @InjectView(R.id.advertise)
    Switch advertiseSwitch;
    @InjectView(R.id.etText)
    EditText etText;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_peripheral);
        ButterKnife.inject(this);

        // Listen for the advertise switch to change
        advertiseSwitch.setOnCheckedChangeListener(this);

        // Get the bluetooth manager and adapter
        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

    }


    @Override
    protected void onPause() {
        advertiseSwitch.setChecked(false);// If you leave this page, stop advertising
        super.onPause();
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        switch (buttonView.getId()) {
            case R.id.advertise:  // Toggle advertising
                if(isChecked) {
                    startAdvertising();
                } else {
                    stopAdvertising();
                }
                break;
        }
    }

    public void startAdvertising() {

        // Make sure they have bluetooth enabled and turned on
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Log.d("auto", "Bluetooth is not turned on");
            Toast.makeText(this, "Bluetooth is not turned on", Toast.LENGTH_SHORT).show();
            return;
        }

        // The device must have advertisement support
        if(!mBluetoothAdapter.isMultipleAdvertisementSupported()) {
            Log.d("auto", "Advertisement Not Supported");
            Toast.makeText(this, "Advertisement Not Supported", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create GATT Service
        dataService = new BluetoothGattService(UUID.fromString(TRANSFER_SERVICE_UUID), BluetoothGattService.SERVICE_TYPE_PRIMARY);

        // Create GattCharacteristic
        dataCharacteristic = new BluetoothGattCharacteristic(UUID.fromString(TRANSFER_CHARACTERISTIC_UUID), BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_INDICATE | BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattDescriptor.PERMISSION_WRITE | BluetoothGattCharacteristic.PERMISSION_READ);

        // Create Config Descriptor
        BluetoothGattDescriptor dataDescriptor = new BluetoothGattDescriptor(UUID.fromString(TRANSFER_DESCIPRTOR_UUID), BluetoothGattDescriptor.PERMISSION_WRITE | BluetoothGattDescriptor.PERMISSION_READ);

        // Add Descriptor
        dataCharacteristic.addDescriptor(dataDescriptor);

        // Add Characteristic
        dataService.addCharacteristic(dataCharacteristic);

        // Open Gatt Server
        gattServer = bluetoothManager.openGattServer(this, gattServerCallback);

        // Add Service
        gattServer.addService(dataService);

        // Start Advertising!
        AdvertiseData.Builder dataBuilder = new AdvertiseData.Builder();
        AdvertiseSettings.Builder settingsBuilder = new AdvertiseSettings.Builder();

        dataBuilder.setIncludeTxPowerLevel(false); //necessity to fit in 31 byte advertisement
        dataBuilder.addServiceUuid(ParcelUuid.fromString(TRANSFER_SERVICE_UUID));
        settingsBuilder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED);
        settingsBuilder.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH);

        // Start Advertising
        BluetoothLeAdvertiser bluetoothLeAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
        bluetoothLeAdvertiser.startAdvertising(settingsBuilder.build(), dataBuilder.build(), advertiseCallback);

    }

    public void stopAdvertising() {

        BluetoothLeAdvertiser bluetoothLeAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();

        // Close gatt server is it is open
        if(gattServer != null) {
            bluetoothLeAdvertiser.stopAdvertising(advertiseCallback);
            gattServer.clearServices();
            gattServer.close();

        }
    }

    public void createMessage() {

        // Chop Message!  You can only advertise 20 bytes at a time through ble
        byte[] message = (etText.getText().toString() + "EOM").getBytes();
        dataToSend = new ArrayList<>();
        dataToSendIndex = 0;

        for(int i=0;i<Math.ceil((float)message.length/(float)20);i++) {

            byte[] slice = new byte[20];

            for(int ii=0;ii<slice.length && i*20 + ii < message.length;ii++) {
                slice[ii] = message[i*20 + ii];
            }

            dataToSend.add(slice);

        }

        // Start sending the message
        sendMessage();
    }

    public void sendMessage() {

        if(dataToSendIndex >= dataToSend.size()) {  // If we have incremented through the message, the message is sent
            Log.d("auto", "PeripheralActivity > sendMessage() > Message Sent");
            // Done Sending!
            return;
        }

        // Set the value of the characteristic to the next data to send
        dataCharacteristic.setValue(dataToSend.get(dataToSendIndex));

        // Notify the subscriber you have changed the value
        gattServer.notifyCharacteristicChanged(btClient, dataCharacteristic, true);

        // Increment index
        dataToSendIndex ++;
    }


    BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            Log.d("auto", "MyActivity > onConnectionStateChange() > Gatt Callback");
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
            Log.d("auto", "MyActivity > onDescriptorWriteRequest() > Gatt Callback");

            Log.d("auto", "MyActivity > onDescriptorWriteRequest() > Device Subscribed!");

            btClient = device;  // Save the device that subscribed to your gatt server

            // You must notify that it was successful
            gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);

            // Start creating the message to send to the device
            createMessage();

        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            super.onNotificationSent(device, status);
            Log.d("auto", "MyActivity > onNotificationSent() > Gatt Callback");

            Log.d("auto", "MyActivity > onNotificationSent() > Notification Sent!");
            sendMessage(); // Send next message

        }
    };

    AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            Log.d("auto", "MyActivity > onStartSuccess() > Advertise Callback");

        }

        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            Log.d("auto", "MyActivity > onStartFailure() > Advertise Callback");

        }
    };

}
