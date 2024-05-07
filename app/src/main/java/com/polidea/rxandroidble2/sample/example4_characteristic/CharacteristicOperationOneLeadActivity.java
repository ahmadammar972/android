package com.polidea.rxandroidble2.sample.example4_characteristic;

import static com.polidea.rxandroidble2.sample.example1_scanning.DiscoveryResultsAdapter.FULL_ONE_ECG_STREAM_DATA;

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
import android.widget.TextView;

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
import com.google.gson.Gson;
import com.jakewharton.rx.ReplayingShare;
import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.RxBleDevice;
import com.polidea.rxandroidble2.sample.DeviceActivity;
import com.polidea.rxandroidble2.sample.R;
import com.polidea.rxandroidble2.sample.SampleApplication;
import com.polidea.rxandroidble2.sample.example1_scanning.ScanActivity;
import com.polidea.rxandroidble2.sample.util.HexString;
import com.polidea.rxandroidble2.sample.util.MQTTTopicResponse;

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
import org.eclipse.paho.client.mqttv3.internal.wire.MqttReceivedMessage;

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
public class CharacteristicOperationOneLeadActivity extends AppCompatActivity implements OnChartValueSelectedListener, MqttCallbackExtended, IMqttActionListener {

    public static final String EXTRA_CHARACTERISTIC_UUID = "extra_uuid";
    @BindView(R.id.connect)
    Button connectButton;
    @BindView(R.id.read)
    Button readButton;
    @BindView(R.id.notify)
    Button notifyButton;
    @BindView(R.id.chart)
    LineChart mChart;
    @BindView(R.id.heart_condition_value)
    TextView heartConditionTV;
    @BindView(R.id.heart_rate)
    TextView heartRateTV;

    private UUID characteristicUuid;
    private final PublishSubject<Boolean> disconnectTriggerSubject = PublishSubject.create();
    private Observable<RxBleConnection> connectionObservable;
    private RxBleDevice bleDevice;
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    MqttAndroidClient mqttAndroidClient;

    final String serverUri = "tcp://199.212.33.168:1883";
    final String subscriptionTopic = "ECGMQTTCOMMCHANNEL";
    final String publishTopic = "tb/mqtt-integration/sensors/ecg/SN-001/data/leadII";
    private String publishMessage;
    private ArrayList<String> ecg_data;
    private ScheduledExecutor executor;


    public static Intent startActivityIntent(Context context, String peripheralMacAddress, UUID characteristicUuid) {
        Intent intent = new Intent(context, CharacteristicOperationOneLeadActivity.class);
        intent.putExtra(DeviceActivity.EXTRA_MAC_ADDRESS, peripheralMacAddress);
        intent.putExtra(EXTRA_CHARACTERISTIC_UUID, characteristicUuid);
        return intent;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example4_production_onelead);
        ButterKnife.bind(this);
        String macAddress = getIntent().getStringExtra(DeviceActivity.EXTRA_MAC_ADDRESS);
        characteristicUuid = (UUID) getIntent().getSerializableExtra(EXTRA_CHARACTERISTIC_UUID);
        if (characteristicUuid.compareTo(FULL_ONE_ECG_STREAM_DATA) == 0) {
            mChart.setVisibility(View.VISIBLE);
            initChart();
        }
        bleDevice = SampleApplication.getRxBleClient(this).getBleDevice(macAddress);
        connectionObservable = prepareConnectionObservable();
        //noinspection ConstantConditions
        getSupportActionBar().setTitle("ECG Patch");
        getSupportActionBar().setSubtitle("One ECG Lead Configuration");

        mqttAndroidClient = SampleApplication.getMQTTClient(this);

        ecg_data = new ArrayList<>();
        executor = new ScheduledExecutor();

        mqttAndroidClient.setCallback(this);

        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setAutomaticReconnect(true);
        mqttConnectOptions.setCleanSession(false);

        try {
            mqttAndroidClient.connect(mqttConnectOptions, null, this);
        } catch (MqttException e) {
            e.printStackTrace();
        }

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

        if (characteristicUuid.compareTo(FULL_ONE_ECG_STREAM_DATA) == 0) {
            LineData data = mChart.getData();
            ILineDataSet set = data.getDataSetByIndex(0);
            if (set == null) {
                set = createSet("Lead II");
                data.addDataSet(set);
            }
            for (int i = 0; i < values.length; i++) {
                //Log.i(getClass().getSimpleName(), "data " + i + " " + values[i]);
                data.addEntry(new Entry(set.getEntryCount(), values[i]), 0);
            }
            data.notifyDataChanged();

            // let the memGraph know it's data has changed
            mChart.notifyDataSetChanged();

            // limit the number of visible entries
            mChart.setVisibleXRangeMaximum(2000);
            // memGraph.setVisibleYRange(30, AxisDependency.LEFT);

            // move to the latest entry
            mChart.moveViewToX(data.getEntryCount());
        }

        String ecgDataPacket = Arrays.toString(values);
        String formattedString = ecgDataPacket
                .replace("[", "")  //remove the right bracket
                .replace("]", "")  //remove the left bracket
                .trim();
        ecg_data.add(formattedString);
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
        executor.stopAsync();
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

    /*
     * ------------------------------------------------------------------------------------
     * MQTT Integration Setup
     * ------------------------------------------------------------------------------------
     */

    private void addToHistory(String mainText) {
        Log.i(getClass().getSimpleName() + " MQTT Log: ", mainText);
    }

    public void subscribeToTopic() {
        try {
            mqttAndroidClient.subscribe(subscriptionTopic, 0, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    addToHistory("Subscribed to " + subscriptionTopic);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    addToHistory("Failed to subscribe to " + subscriptionTopic);
                }
            });

            mqttAndroidClient.subscribe(subscriptionTopic, 0, (topic, message) -> {
                // message Arrived!
                String receivedMsg = new String(message.getPayload());
                Gson gson = new Gson();
                MQTTTopicResponse mqttTopicResponse = gson.fromJson(receivedMsg, MQTTTopicResponse.class);
                addToHistory(" Message Received from topic:  " + topic +  " : " +  receivedMsg);
                heartConditionTV.setText(mqttTopicResponse.getMessage());
                heartRateTV.setText(mqttTopicResponse.getHeartRate());
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

    public void connectComplete(boolean reconnect, String serverURI) {
        if (reconnect) {
            addToHistory(" Reconnected to : " + serverURI);
            // Because Clean Session is true, we need to re-subscribe
            subscribeToTopic();
        } else {
            addToHistory(" Connected to: " + serverURI);
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        addToHistory(" The Connection was lost.");
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        addToHistory(" Incoming message: " + new String(message.getPayload()));
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        addToHistory(" Delivery Complete : " + token.toString());
    }

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
        addToHistory(" Failed to connect to: " + serverUri);
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

            String ts = "" + System.currentTimeMillis();

            publishMessage = String.format("{\"device_id\": \"SN-001\", \"ts\": %s, \"data\": %s}", ts, str);
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
