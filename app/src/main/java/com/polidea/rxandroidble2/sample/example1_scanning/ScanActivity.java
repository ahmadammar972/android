package com.polidea.rxandroidble2.sample.example1_scanning;

import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.app.PendingIntent.FLAG_MUTABLE;
import static com.polidea.rxandroidble2.sample.example1_scanning.DiscoveryResultsAdapter.FULL_ECG_STREAM_DATA;
import static com.polidea.rxandroidble2.sample.example1_scanning.DiscoveryResultsAdapter.FULL_ONE_ECG_STREAM_DATA;
import static com.polidea.rxandroidble2.sample.example1_scanning.DiscoveryResultsAdapter.FULL_THREE_ECG_STREAM_DATA;

import android.Manifest;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.jakewharton.rx.ReplayingShare;
import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.RxBleDevice;
import com.polidea.rxandroidble2.exceptions.BleScanException;
import com.polidea.rxandroidble2.sample.R;
import com.polidea.rxandroidble2.sample.SampleApplication;
import com.polidea.rxandroidble2.sample.example4_characteristic.CharacteristicOperation3LeadsActivity;
import com.polidea.rxandroidble2.sample.example4_characteristic.CharacteristicOperationExampleActivity;
import com.polidea.rxandroidble2.sample.example4_characteristic.CharacteristicOperationExampleFullLeadsActivity;
import com.polidea.rxandroidble2.sample.example4_characteristic.CharacteristicOperationExampleFullLeadsChestActivity;
import com.polidea.rxandroidble2.sample.example4_characteristic.CharacteristicOperationOneLeadActivity;
import com.polidea.rxandroidble2.sample.example4_characteristic.advanced.AdvancedCharacteristicOperationExampleActivity;
import com.polidea.rxandroidble2.sample.util.LocationPermission;
import com.polidea.rxandroidble2.sample.util.ScanExceptionHandler;
import com.polidea.rxandroidble2.scan.BackgroundScanner;
import com.polidea.rxandroidble2.scan.ScanFilter;
import com.polidea.rxandroidble2.scan.ScanResult;
import com.polidea.rxandroidble2.scan.ScanSettings;

import java.util.List;

import butterknife.OnClick;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.subjects.PublishSubject;

public class ScanActivity extends AppCompatActivity {

    private static final int SCAN_REQUEST_CODE = 42;

    static FloatingActionButton scanToggleButton;
    static RecyclerView recyclerView;
    static RecyclerView recyclerViewDiscovery;
    static Button discoverServicesButton;
    static TextView connectionStateView;
    static RxBleClient rxBleClient;
    static Disposable scanDisposable;
    static ScanResultsAdapter resultsAdapter;
    static DiscoveryResultsAdapter adapter;
    static boolean hasClickedScan;
    static boolean hasStartedBKScan;
    static RxBleDevice bleDevice;
    static Disposable stateDisposable;
    static String macAddress;
    static final CompositeDisposable compositeDisposable = new CompositeDisposable();
    static Observable<RxBleConnection> rxBleConnectionObservable;
    static PublishSubject<Boolean> disconnectTriggerSubject = PublishSubject.create();
    static PendingIntent callbackIntent;
    private static Context mContext;


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example1);
        mContext = this;

        scanToggleButton = findViewById(R.id.scan_toggle_btn);
        recyclerView = findViewById(R.id.scan_results);
        recyclerViewDiscovery = findViewById(R.id.scan_results_services);
        discoverServicesButton = findViewById(R.id.discover_services);
        connectionStateView = findViewById(R.id.connection_state);

        if (ContextCompat.checkSelfPermission(ScanActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_DENIED)
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            {
                ActivityCompat.requestPermissions(ScanActivity.this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 2);
                return;
            }
        }

        if (ContextCompat.checkSelfPermission(ScanActivity.this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_DENIED)
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            {
                ActivityCompat.requestPermissions(ScanActivity.this, new String[]{Manifest.permission.BLUETOOTH_SCAN}, 3);
                return;
            }
        }

        rxBleClient = SampleApplication.getRxBleClient(this);
        discoverServicesButton.setEnabled(false);
        callbackIntent = PendingIntent.getBroadcast(this, SCAN_REQUEST_CODE,
                new Intent(this, ScanReceiver.class), 0);
        configureResultList();

        hasStartedBKScan = true;
        if (rxBleClient.isScanRuntimePermissionGranted()) {
            scanBleDeviceInBackground();
        } else {
            LocationPermission.requestLocationPermission(this, rxBleClient);
        }
    }

    public void onScanToggleClick(View view) {
        hasStartedBKScan = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            rxBleClient.getBackgroundScanner().stopBackgroundBleScan(callbackIntent);
        }
        if (isScanning()) {
            scanDisposable.dispose();
        } else {
            if (rxBleClient.isScanRuntimePermissionGranted()) {
                scanBleDevices();
            } else {
                hasClickedScan = true;
                LocationPermission.requestLocationPermission(this, rxBleClient);
            }
        }
        updateButtonUIState();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void onConnectToggleClick(View view) {
        final Disposable disposable = rxBleConnectionObservable
                .flatMapSingle(RxBleConnection::discoverServices)
                .doFinally(ScanActivity::updateUI)
                .subscribe(adapter::swapScanResult, ScanActivity::onConnectionFailure);

        compositeDisposable.add(disposable);

        updateUI();

        Log.w("DiscoverServices", "Discover Service Button Clicked");
    }

    private void scanBleDeviceInBackground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                rxBleClient.getBackgroundScanner().scanBleDeviceInBackground(
                        callbackIntent,
                        new ScanSettings.Builder()
                                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                                .build(),
                        new ScanFilter.Builder()
                                .setDeviceName("ECG BLE")
                                // add custom filters if needed
                                .build()
                );
            } catch (BleScanException scanException) {
                Log.w("BackgroundScanActivity", "Failed to start background scan", scanException);
                ScanExceptionHandler.handleException(this, scanException);
            }
        }
    }

    private void scanBleDevices() {
        if (adapter != null)
            adapter.clearData();
        scanDisposable = rxBleClient.scanBleDevices(
                new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                        .build(),
                new ScanFilter.Builder()
                        .setDeviceName("ECG BLE")
                        .build()
        )
                .observeOn(AndroidSchedulers.mainThread())
                .doFinally(this::dispose)
                .subscribe(resultsAdapter::addScanResult, this::onScanFailure);
    }


    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull final String[] permissions,
                                           @NonNull final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

            if (LocationPermission.isRequestLocationPermissionGranted(requestCode, permissions, grantResults, rxBleClient)
                && hasClickedScan) {
            hasClickedScan = false;
            scanBleDevices();
        } else if (LocationPermission.isRequestLocationPermissionGranted(requestCode, permissions, grantResults, rxBleClient)
                && hasClickedScan) {
            hasStartedBKScan = false;
            scanBleDeviceInBackground();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (isScanning()) {
            /*
             * Stop scanning in onPause callback.
             */
            scanDisposable.dispose();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void configureResultList() {
        recyclerView.setItemAnimator(null);
        LinearLayoutManager recyclerLayoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(recyclerLayoutManager);
        resultsAdapter = new ScanResultsAdapter();
        recyclerView.setAdapter(resultsAdapter);
        resultsAdapter.setOnAdapterItemClickListener(view -> {
            final int childAdapterPosition = recyclerView.getChildAdapterPosition(view);
            final ScanResult itemAtPosition = resultsAdapter.getItemAtPosition(childAdapterPosition);
            onAdapterItemClick(itemAtPosition);
        });
    }

    private boolean isScanning() {
        return scanDisposable != null;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void onAdapterItemClick(ScanResult scanResults) {
        final String macAddress = scanResults.getBleDevice().getMacAddress();
        ScanActivity.macAddress = macAddress;
        bleDevice = SampleApplication.getRxBleClient(this).getBleDevice(macAddress);
        // How to listen for connection state changes
        // Note: it is meant for UI updates only — one should not observeConnectionStateChanges() with BLE connection logic
        stateDisposable = bleDevice.observeConnectionStateChanges()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(ScanActivity::onConnectionStateChange);

        LinearLayoutManager recyclerLayoutManager = new LinearLayoutManager(this);
        recyclerViewDiscovery.setLayoutManager(recyclerLayoutManager);
        adapter = new DiscoveryResultsAdapter(this);
        recyclerViewDiscovery.setAdapter(adapter);
        adapter.setOnAdapterItemClickListener(view -> {
            final int childAdapterPosition = recyclerViewDiscovery.getChildAdapterPosition(view);
            final DiscoveryResultsAdapter.AdapterItem itemAtPosition = adapter.getItem(childAdapterPosition);
            onAdapterDiscoverITemClick(itemAtPosition);
        });

        if (isConnected()) {
            Snackbar.make(connectionStateView, "Already Connected", Snackbar.LENGTH_SHORT).show();
        } else {
            rxBleConnectionObservable = bleDevice.establishConnection(false)
                    .observeOn(AndroidSchedulers.mainThread())
                    .doFinally(ScanActivity::updateUI)
                    .takeUntil(disconnectTriggerSubject)
                    .compose(ReplayingShare.instance());
        }
    }

    private static void onAdapterDiscoverITemClick(DiscoveryResultsAdapter.AdapterItem item) {
        if (item.type == DiscoveryResultsAdapter.AdapterItem.CHARACTERISTIC) {
            Log.i("OnClick Characteristic", item.uuid.toString());
            if(item.uuid.compareTo(FULL_THREE_ECG_STREAM_DATA) == 0) {
                final Intent intent = CharacteristicOperation3LeadsActivity.startActivityIntent(mContext, macAddress, item.uuid);
                triggerDisconnect();
                mContext.startActivity(intent);
            } else if(item.uuid.compareTo(FULL_ONE_ECG_STREAM_DATA) == 0) {
                final Intent intent = CharacteristicOperationOneLeadActivity.startActivityIntent(mContext, macAddress, item.uuid);
                triggerDisconnect();
                mContext.startActivity(intent);
            } else if(item.uuid.compareTo(FULL_ECG_STREAM_DATA) == 0) {
                final Intent intent = CharacteristicOperationExampleFullLeadsChestActivity.startActivityIntent(mContext, macAddress, item.uuid);
                triggerDisconnect();
                mContext.startActivity(intent);
            } else {
                final Intent intent = CharacteristicOperationExampleActivity.startActivityIntent(mContext, macAddress, item.uuid);
                triggerDisconnect();
                mContext.startActivity(intent);
            }
        } else {
            //noinspection Constant Conditions
            Snackbar.make(connectionStateView, R.string.not_clickable, Snackbar.LENGTH_SHORT).show();
        }
    }


    private static boolean isConnected() {
        return bleDevice.getConnectionState() == RxBleConnection.RxBleConnectionState.CONNECTED;
    }

    private static void onMtuReceived(Integer mtu) {
        //noinspection Constant Conditions
        Snackbar.make(connectionStateView, "MTU received: " + mtu, Snackbar.LENGTH_SHORT).show();
    }

    private void onScanFailure(Throwable throwable) {
        if (throwable instanceof BleScanException) {
            ScanExceptionHandler.handleException(this, (BleScanException) throwable);
        }
    }

    private void dispose() {
        scanDisposable = null;
        resultsAdapter.clearScanResults();
        updateButtonUIState();
    }

    private static void onConnectionFailure(Throwable throwable) {
        //noinspection Constant Conditions
        Snackbar.make(connectionStateView, "Connection error: " + throwable, Snackbar.LENGTH_SHORT).show();
        Log.i("error", "Connection error: " + throwable);
    }

    private void updateButtonUIState() {
        Snackbar.make(findViewById(android.R.id.content), "scanning...", Snackbar.LENGTH_SHORT).show();
    }

    private static void triggerDisconnect() {

        if (rxBleConnectionObservable != null) {
            disconnectTriggerSubject.onNext(true);
        }
    }

    public static void updateUI() {
        discoverServicesButton.setEnabled(isConnected());
    }

    public static void onConnectionStateChange(RxBleConnection.RxBleConnectionState newState) {
        if (newState == RxBleConnection.RxBleConnectionState.CONNECTED) {
            hasStartedBKScan = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                rxBleClient.getBackgroundScanner().stopBackgroundBleScan(callbackIntent);
            }
        }
        connectionStateView.setText(newState.toString());
        updateUI();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disconnectTriggerSubject.onNext(true);
        compositeDisposable.clear();

        if (stateDisposable != null) {
            stateDisposable.dispose();
        }
    }

    public static class ScanReceiver extends BroadcastReceiver {
        @RequiresApi(26 /* Build.VERSION_CODES.O */)
        @Override
        public void onReceive(Context context, Intent intent) {
                BackgroundScanner backgroundScanner = SampleApplication.getRxBleClient(context).getBackgroundScanner();
                try {
                    final List<ScanResult> scanResults = backgroundScanner.onScanResultReceived(intent);
                    Log.i("ScanReceiver", "Scan results received: " + scanResults);
                    if (rxBleConnectionObservable == null) {
                        for (int i = 0; i < scanResults.size(); i++) {
                            // BLE MAC: 80:6F:B0:1E:FF:45 --> LaunchPad
                            // PCB MAC: 18:04:ED:BE:BB:A4
                            if (scanResults.get(i).getBleDevice().getName().contains("ECG")) {
                                macAddress = scanResults.get(i).getBleDevice().getMacAddress();
                                bleDevice = SampleApplication.getRxBleClient(context).getBleDevice(macAddress);
                                // How to listen for connection state changes
                                // Note: it is meant for UI updates only — one should not observeConnectionStateChanges() with BLE connection logic
                                stateDisposable = bleDevice.observeConnectionStateChanges()
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .subscribe(ScanActivity::onConnectionStateChange);

                                LinearLayoutManager recyclerLayoutManager = new LinearLayoutManager(context);
                                recyclerViewDiscovery.setLayoutManager(recyclerLayoutManager);
                                adapter = new DiscoveryResultsAdapter(context);
                                recyclerViewDiscovery.setAdapter(adapter);
                                adapter.setOnAdapterItemClickListener(view -> {
                                    final int childAdapterPosition = recyclerViewDiscovery.getChildAdapterPosition(view);
                                    final DiscoveryResultsAdapter.AdapterItem itemAtPosition = adapter.getItem(childAdapterPosition);
                                    onAdapterDiscoverITemClick(itemAtPosition);
                                });
                                rxBleConnectionObservable = bleDevice.establishConnection(false)
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .doFinally(ScanActivity::updateUI)
                                        .takeUntil(disconnectTriggerSubject)
                                        .compose(ReplayingShare.instance());

//                            final Disposable disposable = rxBleConnectionObservable
//                                    .flatMapSingle(rxBleConnection -> rxBleConnection.requestMtu(247))
//                                    .doFinally(ScanActivity::updateUI)
//                                    .subscribe(ScanActivity::onMtuReceived, ScanActivity::onConnectionFailure);
//                            compositeDisposable.add(disposable);

                                final Disposable disposableDiscover = rxBleConnectionObservable
                                        .flatMapSingle(RxBleConnection::discoverServices)
                                        .doFinally(ScanActivity::updateUI)
                                        .subscribe(adapter::swapScanResult, ScanActivity::onConnectionFailure);

                                compositeDisposable.add(disposableDiscover);
                            }
                        }
                    }
                } catch (BleScanException exception) {
                    Log.w("ScanReceiver", "Failed to scan devices", exception);
                }

        }
    }
}
