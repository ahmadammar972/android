package com.polidea.rxandroidble2.sample.example4_characteristic;

import static com.polidea.rxandroidble2.sample.example1_scanning.DiscoveryResultsAdapter.FULL_THREE_ECG_STREAM_DATA;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.util.concurrent.AbstractScheduledService;
import com.jakewharton.rx.ReplayingShare;
import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.RxBleDevice;
import com.polidea.rxandroidble2.sample.DeviceActivity;
import com.polidea.rxandroidble2.sample.R;
import com.polidea.rxandroidble2.sample.SampleApplication;
import com.polidea.rxandroidble2.sample.example1_scanning.ScanActivity;
import com.polidea.rxandroidble2.sample.util.HexString;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.subjects.PublishSubject;

@SuppressLint("NonConstantResourceId")
public class CharacteristicOperation3LeadsActivity extends AppCompatActivity implements OnChartValueSelectedListener {

    public static final String EXTRA_CHARACTERISTIC_UUID = "extra_uuid";
    @BindView(R.id.connect)
    Button connectButton;
    @BindView(R.id.read)
    Button readButton;
    @BindView(R.id.notify)
    Button notifyButton;
    @BindView(R.id.chart)
    LineChart mChart;
    @BindView(R.id.chart1)
    LineChart mChart1;
    @BindView(R.id.chart2)
    LineChart mChart2;

    private UUID characteristicUuid;
    private final PublishSubject<Boolean> disconnectTriggerSubject = PublishSubject.create();
    private Observable<RxBleConnection> connectionObservable;
    private RxBleDevice bleDevice;
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    MqttAndroidClient mqttAndroidClient;

    final String serverUri = "tcp://199.212.33.168:1883";
    String clientId = "ECG-Patch";
    final String subscriptionTopic = "exampleAndroidTopic";
    final String publishTopic = "tb/mqtt-integration/sensors/ecg/SN-001/data/LeadsThree";
    private String publishMessage;
    private ArrayList<String> ecg_data;
    private ScheduledExecutor executor;


    public static Intent startActivityIntent(Context context, String peripheralMacAddress, UUID characteristicUuid) {
        Intent intent = new Intent(context, CharacteristicOperation3LeadsActivity.class);
        intent.putExtra(DeviceActivity.EXTRA_MAC_ADDRESS, peripheralMacAddress);
        intent.putExtra(EXTRA_CHARACTERISTIC_UUID, characteristicUuid);
        return intent;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example4_production);
        ButterKnife.bind(this);
        String macAddress = getIntent().getStringExtra(DeviceActivity.EXTRA_MAC_ADDRESS);
        characteristicUuid = (UUID) getIntent().getSerializableExtra(EXTRA_CHARACTERISTIC_UUID);
        if (characteristicUuid.compareTo(FULL_THREE_ECG_STREAM_DATA) == 0) {
            mChart.setVisibility(View.VISIBLE);
            mChart1.setVisibility(View.VISIBLE);
            mChart2.setVisibility(View.VISIBLE);
            initChart();
            initChart1();
            initChart2();
        }
        bleDevice = SampleApplication.getRxBleClient(this).getBleDevice(macAddress);
        connectionObservable = prepareConnectionObservable();
        //noinspection ConstantConditions
        getSupportActionBar().setSubtitle(getString(R.string.mac_address, macAddress));

        clientId = clientId + System.currentTimeMillis();

        mqttAndroidClient = new MqttAndroidClient(getApplicationContext(), serverUri, clientId);
        mqttAndroidClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                if (reconnect) {
                    addToHistory("Reconnected to : " + serverURI);
                    // Because Clean Session is true, we need to re-subscribe
                    subscribeToTopic();
                } else {
                    addToHistory("Connected to: " + serverURI);
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                addToHistory("The Connection was lost.");
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                addToHistory("Incoming message: " + new String(message.getPayload()));
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {

            }
        });
        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setAutomaticReconnect(true);
        mqttConnectOptions.setCleanSession(false);

        try {
            //addToHistory("Connecting to " + serverUri);
            mqttAndroidClient.connect(mqttConnectOptions, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    DisconnectedBufferOptions disconnectedBufferOptions = new DisconnectedBufferOptions();
                    disconnectedBufferOptions.setBufferEnabled(true);
                    disconnectedBufferOptions.setBufferSize(100);
                    disconnectedBufferOptions.setPersistBuffer(false);
                    disconnectedBufferOptions.setDeleteOldestMessages(false);
                    mqttAndroidClient.setBufferOpts(disconnectedBufferOptions);
                    subscribeToTopic();
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    addToHistory("Failed to connect to: " + serverUri);
                }
            });

        } catch (MqttException ex) {
            ex.printStackTrace();
        }
        ecg_data = new ArrayList<>();
        executor = new ScheduledExecutor();
    }

    private void addToHistory(String mainText) {
        Log.i(getClass().getSimpleName() + "MQTT Log: ", mainText);
    }

    private Observable<RxBleConnection> prepareConnectionObservable() {
        return bleDevice
                .establishConnection(false)
                .takeUntil(disconnectTriggerSubject)
                .compose(ReplayingShare.instance());
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @OnClick(R.id.connect)
    public void onConnectToggleClick() {
        if (isConnected()) {
            triggerDisconnect();
        } else {
            final Disposable connectionDisposable = connectionObservable
                    .flatMapSingle(RxBleConnection::discoverServices)
                    .flatMapSingle(rxBleDeviceServices -> rxBleDeviceServices.getCharacteristic(characteristicUuid))
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnSubscribe(disposable -> connectButton.setText(R.string.connecting))
                    .subscribe(
                            characteristic -> {
                                updateUI(characteristic);
                                Log.i(getClass().getSimpleName(), "Hey, connection has been established!");
                            },
                            this::onConnectionFailure,
                            this::onConnectionFinished
                    );
            compositeDisposable.add(connectionDisposable);

            final Disposable disposable = connectionObservable
                    .flatMapSingle(rxBleConnection -> rxBleConnection.requestMtu(247))
                    .doFinally(ScanActivity::updateUI)
                    .subscribe(this::onMtuReceived, this::onConnectionFailure);
            compositeDisposable.add(disposable);
        }
    }

    private void onMtuReceived(Integer mtu) {
        //noinspection Constant Conditions
        Snackbar.make(findViewById(android.R.id.content), "MTU received: " + mtu, Snackbar.LENGTH_SHORT).show();
    }

    @OnClick(R.id.read)
    public void onReadClick() {
        if (isConnected()) {
            final Disposable disposable = connectionObservable
                    .firstOrError()
                    .flatMap(rxBleConnection -> rxBleConnection.readCharacteristic(characteristicUuid))
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(bytes -> {
                        Snackbar.make(findViewById(android.R.id.content), "Read Value: " + HexString.bytesToHex(bytes), Snackbar.LENGTH_SHORT).show();
                    }, this::onReadFailure);
            compositeDisposable.add(disposable);
        }
    }

    //@OnClick(R.id.write)
    public void onWriteClick() {
        if (isConnected()) {
            final Disposable disposable = connectionObservable
                    .firstOrError()
                    .flatMap(rxBleConnection -> rxBleConnection.writeCharacteristic(characteristicUuid, getInputBytes()))
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            bytes -> onWriteSuccess(),
                            this::onWriteFailure
                    );
            compositeDisposable.add(disposable);
        }
    }

    @OnClick(R.id.notify)
    public void onNotifyClick() {
        if (isConnected()) {
            final Disposable disposable = connectionObservable
                    .flatMap(rxBleConnection -> rxBleConnection.setupNotification(characteristicUuid))
                    .doOnNext(notificationObservable -> runOnUiThread(this::notificationHasBeenSetUp))
                    .flatMap(notificationObservable -> notificationObservable)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(this::onNotificationReceived, this::onNotificationSetupFailure);
            compositeDisposable.add(disposable);
        }
    }

    private boolean isConnected() {
        return bleDevice.getConnectionState() == RxBleConnection.RxBleConnectionState.CONNECTED;
    }

    private void onConnectionFailure(Throwable throwable) {
        //noinspection Constant Conditions
        Snackbar.make(findViewById(R.id.main), "Connection error: " + throwable, Snackbar.LENGTH_SHORT).show();
        updateUI(null);
    }

    private void onConnectionFinished() {
        updateUI(null);
    }

    private void onReadFailure(Throwable throwable) {
        //noinspection Constant Conditions
        Snackbar.make(findViewById(R.id.main), "Read error: " + throwable, Snackbar.LENGTH_SHORT).show();
    }

    private void onWriteSuccess() {
        //noinspection Constant Conditions
        Snackbar.make(findViewById(R.id.main), "Write success", Snackbar.LENGTH_SHORT).show();
    }

    private void onWriteFailure(Throwable throwable) {
        //noinspection Constant Conditions
        Snackbar.make(findViewById(R.id.main), "Write error: " + throwable, Snackbar.LENGTH_SHORT).show();
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void onNotificationReceived(byte[] bytes) {
        //noinspection Constant Conditions
        float[] values = HexString.bytesToDecimal(bytes);

        if (characteristicUuid.compareTo(FULL_THREE_ECG_STREAM_DATA) == 0) {
            LineData data = mChart.getData();
            LineData data1 = mChart1.getData();
            LineData data2 = mChart2.getData();

            ILineDataSet set = data.getDataSetByIndex(0);
            ILineDataSet set1 = data1.getDataSetByIndex(0);
            ILineDataSet set2 = data1.getDataSetByIndex(0);
            if (set == null) {
                set = createSet("Lead I");
                data.addDataSet(set);
            }
            if (set1 == null) {
                set1 = createSet("Lead II");
                data1.addDataSet(set1);
            }
            if (set2 == null) {
                set2 = createSet("Lead avF");
                data2.addDataSet(set2);
            }
            for (int i = 0; i < values.length; i = i + 2) {
                Log.i(getClass().getSimpleName(), "data " + i + " " + values[i]);
                data.addEntry(new Entry(set.getEntryCount(), values[i]), 0);
                data1.addEntry(new Entry(set1.getEntryCount(), values[i + 1]), 0);
                float lead3 = (values[i + 1] - values[i]);
                float leadaVF = (values[i + 1] + lead3) / 2;
                data2.addEntry(new Entry(set2.getEntryCount(), (leadaVF)), 0);
            }
            data.notifyDataChanged();
            data1.notifyDataChanged();
            data2.notifyDataChanged();

            // let the memGraph know it's data has changed
            mChart.notifyDataSetChanged();
            mChart1.notifyDataSetChanged();
            mChart2.notifyDataSetChanged();

            // limit the number of visible entries
            mChart.setVisibleXRangeMaximum(2000);
            mChart1.setVisibleXRangeMaximum(2000);
            mChart2.setVisibleXRangeMaximum(2000);
            // memGraph.setVisibleYRange(30, AxisDependency.LEFT);

            // move to the latest entry
            mChart.moveViewToX(data.getEntryCount());
            mChart1.moveViewToX(data.getEntryCount());
            mChart2.moveViewToX(data.getEntryCount());
        }

        String ecgDataPacket = Arrays.toString(values);
//        String formattedString = ecgDataPacket
//                .replace("[", "")  //remove the right bracket
//                .replace("]", "")  //remove the left bracket
//                .trim();
        ecg_data.add(ecgDataPacket);
    }

    private void onNotificationSetupFailure(Throwable throwable) {
        //noinspection Constant Conditions
        Snackbar.make(findViewById(R.id.main), "Notifications error: " + throwable, Snackbar.LENGTH_SHORT).show();
        executor.stopAsync();
    }

    private void notificationHasBeenSetUp() {
        //noinspection Constant Conditions
        Snackbar.make(findViewById(R.id.main), "Notifications has been set up", Snackbar.LENGTH_SHORT).show();
        executor.startAsync();
    }

    private void triggerDisconnect() {
        disconnectTriggerSubject.onNext(true);
    }

    /**
     * This method updates the UI to a proper state.
     *
     * @param characteristic a nullable {@link BluetoothGattCharacteristic}. If it is null then UI is assuming a disconnected state.
     */
    private void updateUI(BluetoothGattCharacteristic characteristic) {
        connectButton.setText(characteristic != null ? R.string.disconnect : R.string.connect);
        readButton.setEnabled(hasProperty(characteristic, BluetoothGattCharacteristic.PROPERTY_READ));
        notifyButton.setEnabled(hasProperty(characteristic, BluetoothGattCharacteristic.PROPERTY_NOTIFY));
    }

    private boolean hasProperty(BluetoothGattCharacteristic characteristic, int property) {
        return characteristic != null && (characteristic.getProperties() & property) > 0;
    }

    private byte[] getInputBytes() {
        return HexString.hexToBytes("01");
    }

    @Override
    protected void onPause() {
        super.onPause();
        compositeDisposable.clear();
    }

    public void subscribeToTopic() {
        try {
            mqttAndroidClient.subscribe(subscriptionTopic, 0, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    addToHistory("Subscribed!");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    addToHistory("Failed to subscribe");
                }
            });

            // THIS DOES NOT WORK!
            mqttAndroidClient.subscribe(subscriptionTopic, 0, new IMqttMessageListener() {
                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    // message Arrived!
                    Log.v("MQTT", "Message: " + topic + " : " + new String(message.getPayload()));
                }
            });

        } catch (MqttException ex) {
            System.err.println("Exception whilst subscribing");
            ex.printStackTrace();
        }
    }

    public void publishMessage() {

        try {
            MqttMessage message = new MqttMessage();
            message.setPayload(publishMessage.getBytes());
            mqttAndroidClient.publish(publishTopic, message);
            addToHistory("Message Published");
            if (!mqttAndroidClient.isConnected()) {
                addToHistory(mqttAndroidClient.getBufferedMessageCount() + " messages in buffer.");
            }
        } catch (MqttException e) {
            System.err.println("Error Publishing: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void initChart() {
        mChart.setOnChartValueSelectedListener(this);
        mChart.getDescription().setEnabled(true);

        // enable touch gestures
        mChart.setTouchEnabled(true);

        // enable scaling and dragging
        mChart.setDragEnabled(true);
        mChart.setScaleEnabled(true);
        mChart.setDrawGridBackground(false);

        // if disabled, scaling can be done on x- and y-axis separately
        mChart.setPinchZoom(true);

        // set an alternative background color
        mChart.setBackgroundColor(Color.argb(100, 245, 149, 154));

        LineData data = new LineData();
        data.setValueTextColor(Color.WHITE);

        // add empty data
        mChart.setData(data);

        mChart.getDescription().setText("ECG");
        mChart.getDescription().setTextColor(Color.WHITE);

        // get the legend (only possible after setting data)
        Legend l = mChart.getLegend();

        // modify the legend ...
        l.setForm(Legend.LegendForm.LINE);
        l.setTextColor(Color.WHITE);

        XAxis xl = mChart.getXAxis();
        xl.setTextColor(Color.WHITE);
        xl.setDrawGridLines(true);
        xl.setAvoidFirstLastClipping(true);
        xl.setEnabled(true);
        xl.setDrawLabels(false);

        YAxis leftAxis = mChart.getAxisLeft();
        leftAxis.setTextColor(Color.WHITE);
        leftAxis.setAxisMaximum(60000f);
        leftAxis.setAxisMinimum(-20000f);
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(Color.WHITE);
        leftAxis.setDrawLabels(false);

        YAxis rightAxis = mChart.getAxisRight();
        rightAxis.setEnabled(false);
    }

    private void initChart1() {
        mChart1.setOnChartValueSelectedListener(this);
        mChart1.getDescription().setEnabled(true);

        // enable touch gestures
        mChart1.setTouchEnabled(true);

        // enable scaling and dragging
        mChart1.setDragEnabled(true);
        mChart1.setScaleEnabled(true);
        mChart1.setDrawGridBackground(false);

        // if disabled, scaling can be done on x- and y-axis separately
        mChart1.setPinchZoom(true);

        // set an alternative background color
        mChart1.setBackgroundColor(Color.argb(100, 245, 149, 154));

        LineData data = new LineData();
        data.setValueTextColor(Color.WHITE);

        // add empty data
        mChart1.setData(data);

        mChart1.getDescription().setText("ECG");
        mChart1.getDescription().setTextColor(Color.WHITE);

        // get the legend (only possible after setting data)
        Legend l = mChart1.getLegend();

        // modify the legend ...
        l.setForm(Legend.LegendForm.LINE);
        l.setTextColor(Color.WHITE);

        XAxis xl = mChart1.getXAxis();
        xl.setTextColor(Color.WHITE);
        xl.setDrawGridLines(false);
        xl.setAvoidFirstLastClipping(true);
        xl.setEnabled(true);

        YAxis leftAxis = mChart1.getAxisLeft();
        leftAxis.setTextColor(Color.WHITE);
        leftAxis.setAxisMaximum(60000f);
        leftAxis.setAxisMinimum(-20000f);
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(Color.WHITE);

        YAxis rightAxis = mChart1.getAxisRight();
        rightAxis.setEnabled(false);
    }

    private void initChart2() {
        mChart2.setOnChartValueSelectedListener(this);
        mChart2.getDescription().setEnabled(true);

        // enable touch gestures
        mChart2.setTouchEnabled(true);

        // enable scaling and dragging
        mChart2.setDragEnabled(true);
        mChart2.setScaleEnabled(true);
        mChart2.setDrawGridBackground(false);

        // if disabled, scaling can be done on x- and y-axis separately
        mChart2.setPinchZoom(true);

        // set an alternative background color
        mChart2.setBackgroundColor(Color.argb(100, 245, 149, 154));

        LineData data = new LineData();
        data.setValueTextColor(Color.WHITE);

        // add empty data
        mChart2.setData(data);
        mChart2.getDescription().setText("ECG");
        mChart2.getDescription().setTextColor(Color.WHITE);

        // get the legend (only possible after setting data)
        Legend l = mChart2.getLegend();

        // modify the legend ...
        l.setForm(Legend.LegendForm.LINE);
        l.setTextColor(Color.WHITE);

        XAxis xl = mChart2.getXAxis();
        xl.setTextColor(Color.WHITE);
        xl.setDrawGridLines(false);
        xl.setAvoidFirstLastClipping(true);
        xl.setEnabled(true);

        YAxis leftAxis = mChart2.getAxisLeft();
        leftAxis.setTextColor(Color.WHITE);
        leftAxis.setAxisMaximum(60000f);
        leftAxis.setAxisMinimum(-20000f);
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(Color.WHITE);

        YAxis rightAxis = mChart2.getAxisRight();
        rightAxis.setEnabled(false);
    }

    private LineDataSet createSet(String setName) {
        LineDataSet set = new LineDataSet(null, setName);
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setColor(Color.BLACK);
        set.setDrawCircles(false);
        set.setLineWidth(2f);
        set.setFillAlpha(65);
        set.setFillColor(Color.BLACK);
        set.setHighLightColor(Color.rgb(244, 117, 117));
        set.setValueTextColor(Color.WHITE);
        set.setValueTextSize(9f);
        set.setDrawValues(false);
        return set;
    }

    @Override
    public void onValueSelected(Entry e, Highlight h) {
    }

    @Override
    public void onNothingSelected() {
    }

    class ScheduledExecutor extends AbstractScheduledService {

        @Override
        protected void startUp() {
            Log.i(getClass().getSimpleName() + "MQTT Job: ", "Job started at: " + new java.util.Date());
        }

        @RequiresApi(api = Build.VERSION_CODES.N)
        @Override
        protected void runOneIteration() throws Exception {
            String str = Stream.of(ecg_data)
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
            publishMessage = String.format("{\"device_id\": \"SN-001\", \"data\": %s}", str);
            publishMessage();
            ecg_data.clear();
            Log.i(getClass().getSimpleName() + "MQTT Job: ", "Running: " + new java.util.Date());
        }

        @Override
        protected Scheduler scheduler() {
            // execute every second
            Log.i(getClass().getSimpleName() + "MQTT Scheduler: ", "Started at: " + new java.util.Date());
            return Scheduler.newFixedRateSchedule(0, 3, TimeUnit.SECONDS);
        }

        @Override
        protected void shutDown() {
            Log.i(getClass().getSimpleName() + "MQTT Job: ", "Job terminated at: " + new java.util.Date());
        }
    }
}
