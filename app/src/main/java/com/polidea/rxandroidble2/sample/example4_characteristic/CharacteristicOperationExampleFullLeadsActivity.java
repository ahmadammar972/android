package com.polidea.rxandroidble2.sample.example4_characteristic;

import static com.polidea.rxandroidble2.sample.example1_scanning.DiscoveryResultsAdapter.FULL_ECG_STREAM_DATA;

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
public class CharacteristicOperationExampleFullLeadsActivity extends AppCompatActivity implements OnChartValueSelectedListener {

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
    @BindView(R.id.chart3)
    LineChart mChart3;
    @BindView(R.id.chart4)
    LineChart mChart4;
    @BindView(R.id.chart5)
    LineChart mChart5;


    private UUID characteristicUuid;
    private final PublishSubject<Boolean> disconnectTriggerSubject = PublishSubject.create();
    private Observable<RxBleConnection> connectionObservable;
    private RxBleDevice bleDevice;
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    MqttAndroidClient mqttAndroidClient;

    final String serverUri = "tcp://199.212.33.168:1883";
    String clientId = "ECG-Patch";
    final String subscriptionTopic = "exampleAndroidTopic";
    final String publishTopic = "tb/mqtt-integration/sensors/ecg/SN-001/data/LeadsLimbs";
    private String publishMessage;
    private ArrayList<String> ecg_data;
    private ScheduledExecutor executor;


    public static Intent startActivityIntent(Context context, String peripheralMacAddress, UUID characteristicUuid) {
        Intent intent = new Intent(context, CharacteristicOperationExampleFullLeadsActivity.class);
        intent.putExtra(DeviceActivity.EXTRA_MAC_ADDRESS, peripheralMacAddress);
        intent.putExtra(EXTRA_CHARACTERISTIC_UUID, characteristicUuid);
        return intent;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_example4_production_fullleads);
        ButterKnife.bind(this);
        String macAddress = getIntent().getStringExtra(DeviceActivity.EXTRA_MAC_ADDRESS);
        characteristicUuid = (UUID) getIntent().getSerializableExtra(EXTRA_CHARACTERISTIC_UUID);
        if(characteristicUuid.compareTo(FULL_ECG_STREAM_DATA) == 0) {
            mChart.setVisibility(View.VISIBLE);
            mChart1.setVisibility(View.VISIBLE);
            mChart2.setVisibility(View.VISIBLE);
            mChart3.setVisibility(View.VISIBLE);
            mChart4.setVisibility(View.VISIBLE);
            mChart5.setVisibility(View.VISIBLE);
            initChart(mChart, 1);
            initChart(mChart1, 2);
            initChart(mChart2, 3);
            initChart(mChart3, 4);
            initChart(mChart4, 5);
            initChart(mChart5, 6);
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

        } catch (MqttException ex){
            ex.printStackTrace();
        }
        ecg_data = new ArrayList<>();
        executor = new ScheduledExecutor();
    }

    private void addToHistory(String mainText){
        Log.i(getClass().getSimpleName(), "LOG: " + mainText);
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

        if(characteristicUuid.compareTo(FULL_ECG_STREAM_DATA) == 0) {
            LineData data = mChart.getData();
            LineData data1 = mChart1.getData();
            LineData data2 = mChart2.getData();
            LineData data3 = mChart3.getData();
            LineData data4 = mChart4.getData();
            LineData data5 = mChart5.getData();

            ILineDataSet set = data.getDataSetByIndex(0);
            ILineDataSet set1 = data1.getDataSetByIndex(0);
            ILineDataSet set2 = data2.getDataSetByIndex(0);
            ILineDataSet set3 = data3.getDataSetByIndex(0);
            ILineDataSet set4 = data4.getDataSetByIndex(0);
            ILineDataSet set5 = data5.getDataSetByIndex(0);
            if (set == null || set1 == null || set2 == null || set3 == null || set4 == null || set5 == null) {
                set = createSet("Lead I");
                data.addDataSet(set);

                set1 = createSet("Lead II");
                data1.addDataSet(set1);

                set2 = createSet("Lead III");
                data2.addDataSet(set2);

                set3 = createSet("aVR");
                data3.addDataSet(set3);

                set4 = createSet("aVL");
                data4.addDataSet(set4);

                set5 = createSet("aVF");
                data5.addDataSet(set5);
            }
            data.addEntry(new Entry(set.getEntryCount(), values[1]), 0); // lead I
            data1.addEntry(new Entry(set1.getEntryCount(), values[2]), 0); // lead II
            float lead3 = values[2] - values[1];
            data2.addEntry(new Entry(set2.getEntryCount(), lead3), 0); // lead III
            data3.addEntry(new Entry(set3.getEntryCount(), (((values[1] + values [2]) / 2)) * -1), 0); // lead -aVR
            data4.addEntry(new Entry(set4.getEntryCount(), ((values[1] - lead3)/2) ), 0); // lead avL
            data5.addEntry(new Entry(set5.getEntryCount(), ((values[2] + lead3)/2)), 0); // lead aVF
            Log.i(getClass().getSimpleName(), "data " + values[3]);
            data.notifyDataChanged();
            data1.notifyDataChanged();
            data2.notifyDataChanged();
            data3.notifyDataChanged();
            data4.notifyDataChanged();
            data5.notifyDataChanged();

            // let the memGraph know it's data has changed
            mChart.notifyDataSetChanged();
            mChart1.notifyDataSetChanged();
            mChart2.notifyDataSetChanged();
            mChart3.notifyDataSetChanged();
            mChart4.notifyDataSetChanged();
            mChart5.notifyDataSetChanged();

            // limit the number of visible entries
            mChart.setVisibleXRangeMaximum(250);
            mChart1.setVisibleXRangeMaximum(250);
            mChart2.setVisibleXRangeMaximum(250);
            mChart3.setVisibleXRangeMaximum(250);
            mChart4.setVisibleXRangeMaximum(250);
            mChart5.setVisibleXRangeMaximum(250);
            // memGraph.setVisibleYRange(30, AxisDependency.LEFT);

            // move to the latest entry
            mChart.moveViewToX(data.getEntryCount());
            mChart1.moveViewToX(data.getEntryCount());
            mChart2.moveViewToX(data.getEntryCount());
            mChart3.moveViewToX(data.getEntryCount());
            mChart4.moveViewToX(data.getEntryCount());
            mChart5.moveViewToX(data.getEntryCount());
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

    public void subscribeToTopic(){
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
                    Log.v("MQTT","Message: " + topic + " : " + new String(message.getPayload()));
                }
            });

        } catch (MqttException ex){
            System.err.println("Exception whilst subscribing");
            ex.printStackTrace();
        }
    }

    public void publishMessage(){

        try {
            MqttMessage message = new MqttMessage();
            message.setPayload(publishMessage.getBytes());
            mqttAndroidClient.publish(publishTopic, message);
            addToHistory("Message Published");
            if(!mqttAndroidClient.isConnected()){
                addToHistory(mqttAndroidClient.getBufferedMessageCount() + " messages in buffer.");
            }
        } catch (MqttException e) {
            System.err.println("Error Publishing: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void initChart(LineChart mChart, int channelType) {
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
        mChart.setBackgroundColor(Color.argb(100,245,149,154));

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
        xl.setDrawGridLines(false);
        xl.setAvoidFirstLastClipping(true);
        xl.setEnabled(true);

        if (channelType == 1){
            YAxis leftAxis = mChart.getAxisLeft();
            leftAxis.setTextColor(Color.WHITE);
            leftAxis.setAxisMaximum(30000f);
            leftAxis.setAxisMinimum(-10000f);
            leftAxis.setDrawGridLines(true);
            leftAxis.setGridColor(Color.WHITE);

            YAxis rightAxis = mChart.getAxisRight();
            rightAxis.setEnabled(false);
        } if (channelType == 2){
            YAxis leftAxis = mChart.getAxisLeft();
            leftAxis.setTextColor(Color.WHITE);
            leftAxis.setAxisMaximum(70000f);
            leftAxis.setAxisMinimum(0f);
            leftAxis.setDrawGridLines(true);
            leftAxis.setGridColor(Color.WHITE);

            YAxis rightAxis = mChart.getAxisRight();
            rightAxis.setEnabled(false);
        } if (channelType == 3){
            YAxis leftAxis = mChart.getAxisLeft();
            leftAxis.setTextColor(Color.WHITE);
            leftAxis.setAxisMaximum(40000f);
            leftAxis.setAxisMinimum(-10000f);
            leftAxis.setDrawGridLines(true);
            leftAxis.setGridColor(Color.WHITE);

            YAxis rightAxis = mChart.getAxisRight();
            rightAxis.setEnabled(false);
        } else if (channelType == 4){
            YAxis leftAxis = mChart.getAxisLeft();
            leftAxis.setTextColor(Color.WHITE);
            leftAxis.setAxisMaximum(0f);
            leftAxis.setAxisMinimum(-60000f);
            leftAxis.setDrawGridLines(true);
            leftAxis.setGridColor(Color.WHITE);

            YAxis rightAxis = mChart.getAxisRight();
            rightAxis.setEnabled(false);
        } else if (channelType == 5){
            YAxis leftAxis = mChart.getAxisLeft();
            leftAxis.setTextColor(Color.WHITE);
            leftAxis.setAxisMaximum(10000f);
            leftAxis.setAxisMinimum(-10000f);
            leftAxis.setDrawGridLines(true);
            leftAxis.setGridColor(Color.WHITE);

            YAxis rightAxis = mChart.getAxisRight();
            rightAxis.setEnabled(false);
        } else if (channelType == 6){
            YAxis leftAxis = mChart.getAxisLeft();
            leftAxis.setTextColor(Color.WHITE);
            leftAxis.setAxisMaximum(50000f);
            leftAxis.setAxisMinimum(-10000f);
            leftAxis.setDrawGridLines(true);
            leftAxis.setGridColor(Color.WHITE);

            YAxis rightAxis = mChart.getAxisRight();
            rightAxis.setEnabled(false);
        }

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
