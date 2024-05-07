package com.polidea.rxandroidble2.sample.example2_connection;

import static com.polidea.rxandroidble2.sample.example1_scanning.DiscoveryResultsAdapter.FULL_THREE_ECG_STREAM_DATA;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import android.widget.Button;
import android.widget.TextView;

import com.jakewharton.rx.ReplayingShare;
import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.RxBleDevice;
import com.polidea.rxandroidble2.sample.DeviceActivity;
import com.polidea.rxandroidble2.sample.R;
import com.polidea.rxandroidble2.sample.SampleApplication;
import com.polidea.rxandroidble2.sample.example4_characteristic.advanced.AdvancedCharacteristicOperationExampleActivity;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.subjects.PublishSubject;

public class ConnectionExampleActivity extends AppCompatActivity {

    @BindView(R.id.connection_state)
    TextView connectionStateView;
    @BindView(R.id.connect_toggle)
    Button connectButton;
    @BindView(R.id.autoconnect)
    SwitchCompat autoConnectToggleSwitch;
    @BindView(R.id.discover_services)
    Button discoverServicesButton;
    @BindView(R.id.scan_results)
    RecyclerView recyclerView;
    private DiscoveryResultsAdapter adapter;
    private String macAddress;
    private RxBleDevice bleDevice;
    public Observable<RxBleConnection> rxBleConnectionObservable;
    private Disposable stateDisposable;
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();
    private PublishSubject<Boolean> disconnectTriggerSubject = PublishSubject.create();

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @OnClick(R.id.connect_toggle)
    public void onConnectToggleClick() {
        if (isConnected()) {
            triggerDisconnect();
        } else {
            rxBleConnectionObservable = bleDevice.establishConnection(autoConnectToggleSwitch.isChecked())
                    .observeOn(AndroidSchedulers.mainThread())
                    .doFinally(this::updateUI)
                    .takeUntil(disconnectTriggerSubject)
                    .compose(ReplayingShare.instance());

            final Disposable disposable = rxBleConnectionObservable
                    .flatMapSingle(rxBleConnection -> rxBleConnection.requestMtu(247))
                    .doFinally(this::updateUI)
                    .subscribe(this::onMtuReceived, this::onConnectionFailure);
            compositeDisposable.add(disposable);
        }
    }

    @OnClick(R.id.discover_services)
    public void onServiceDiscoveryClick(){
        final Disposable disposable = rxBleConnectionObservable
                .flatMapSingle(RxBleConnection::discoverServices)
                .doFinally(this::updateUI)
                .subscribe(adapter::swapScanResult, this::onConnectionFailure);

        compositeDisposable.add(disposable);

        updateUI();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example2);
        ButterKnife.bind(this);
        discoverServicesButton.setEnabled(false);
        macAddress = getIntent().getStringExtra(DeviceActivity.EXTRA_MAC_ADDRESS);
        setTitle(getString(R.string.mac_address, macAddress));
        bleDevice = SampleApplication.getRxBleClient(this).getBleDevice(macAddress);
        // How to listen for connection state changes
        // Note: it is meant for UI updates only — one should not observeConnectionStateChanges() with BLE connection logic
        stateDisposable = bleDevice.observeConnectionStateChanges()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onConnectionStateChange);

        configureResultList();
    }


    private void configureResultList() {
        recyclerView.setHasFixedSize(true);
        LinearLayoutManager recyclerLayoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(recyclerLayoutManager);
        adapter = new DiscoveryResultsAdapter();
        recyclerView.setAdapter(adapter);
        adapter.setOnAdapterItemClickListener(view -> {
            final int childAdapterPosition = recyclerView.getChildAdapterPosition(view);
            final DiscoveryResultsAdapter.AdapterItem itemAtPosition = adapter.getItem(childAdapterPosition);
            onAdapterITemClick(itemAtPosition);
        });
    }

    private void onAdapterITemClick(DiscoveryResultsAdapter.AdapterItem item) {
        if (item.type == DiscoveryResultsAdapter.AdapterItem.CHARACTERISTIC) {
            if(item.uuid.compareTo(FULL_THREE_ECG_STREAM_DATA) == 0) {
                final Intent intent = AdvancedCharacteristicOperationExampleActivity.startActivityIntent(this, macAddress, item.uuid);
                triggerDisconnect();
                startActivity(intent);
            } else {
                final Intent intent = AdvancedCharacteristicOperationExampleActivity.startActivityIntent(this, macAddress, item.uuid);
                triggerDisconnect();
                startActivity(intent);
            }
        } else {
            //noinspection ConstantConditions
            Snackbar.make(findViewById(android.R.id.content), R.string.not_clickable, Snackbar.LENGTH_SHORT).show();
        }
    }

    private boolean isConnected() {
        return bleDevice.getConnectionState() == RxBleConnection.RxBleConnectionState.CONNECTED;
    }

    private void onConnectionFailure(Throwable throwable) {
        //noinspection ConstantConditions
        Snackbar.make(findViewById(android.R.id.content), "Connection error: " + throwable, Snackbar.LENGTH_SHORT).show();
    }

    private void onConnectionStateChange(RxBleConnection.RxBleConnectionState newState) {
        connectionStateView.setText(newState.toString());
        updateUI();
    }

    private void onMtuReceived(Integer mtu) {
        //noinspection ConstantConditions
        Snackbar.make(findViewById(android.R.id.content), "MTU received: " + mtu, Snackbar.LENGTH_SHORT).show();
    }

    private void triggerDisconnect() {

        if (rxBleConnectionObservable != null) {
            disconnectTriggerSubject.onNext(true);
        }
    }

    private void updateUI() {
        final boolean connected = isConnected();
        connectButton.setText(connected ? R.string.disconnect : R.string.connect);
        autoConnectToggleSwitch.setEnabled(!connected);
        discoverServicesButton.setEnabled(isConnected());
    }

    @Override
    protected void onPause() {
        super.onPause();
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
}
