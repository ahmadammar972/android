package com.polidea.rxandroidble2.sample.example1_scanning;


import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.polidea.rxandroidble2.RxBleDeviceServices;
import com.polidea.rxandroidble2.sample.R;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import butterknife.BindView;
import butterknife.ButterKnife;

public class DiscoveryResultsAdapter extends RecyclerView.Adapter<DiscoveryResultsAdapter.ViewHolder> {

    private final Context mContext;
    public final static UUID FULL_ECG_SERVICE_UUID = UUID.fromString("f000ba55-0451-4000-b000-000000000000");
    public final static UUID FULL_ECG_NUM_LEAD = UUID.fromString("f0003bad-0451-4000-b000-000000000000");
    public final static UUID FULL_ECG_STREAM_FLAG = UUID.fromString("f0004bad-0451-4000-b000-000000000000");
    public final static UUID FULL_ECG_STREAM_DATA = UUID.fromString("f0002bad-0451-4000-b000-000000000000");
    public final static UUID FULL_ECG_ACQ_FLAG = UUID.fromString("f0005bad-0451-4000-b000-000000000000");

    public final static UUID FULL_ONE_ECG_SERVICE_UUID = UUID.fromString("f000bc66-0451-4000-b000-000000000000");
    public final static UUID FULL_ONE_ECG_NUM_LEAD = UUID.fromString("f0003bcd-0451-4000-b000-000000000000");
    public final static UUID FULL_ONE_ECG_STREAM_FLAG = UUID.fromString("f0004bcd-0451-4000-b000-000000000000");
    public final static UUID FULL_ONE_ECG_STREAM_DATA = UUID.fromString("f0002bcd-0451-4000-b000-000000000000");
    public final static UUID FULL_ONE_ECG_ACQ_FLAG = UUID.fromString("f0005bcd-0451-4000-b000-000000000000");

    public final static UUID FULL_THREE_ECG_SERVICE_UUID = UUID.fromString("f000bd77-0451-4000-b000-000000000000");
    public final static UUID FULL_THREE_ECG_NUM_LEAD = UUID.fromString("f0003bdd-0451-4000-b000-000000000000");
    public final static UUID FULL_THREE_ECG_STREAM_FLAG = UUID.fromString("f0004bdd-0451-4000-b000-000000000000");
    public final static UUID FULL_THREE_ECG_STREAM_DATA = UUID.fromString("f0002bdd-0451-4000-b000-000000000000");
    public final static UUID FULL_THREE_ECG_ACQ_FLAG = UUID.fromString("f0005bdd-0451-4000-b000-000000000000");


    public DiscoveryResultsAdapter(Context context) {
        mContext = context;
    }

    public static class AdapterItem {

        static final int SERVICE = 1;
        static final int CHARACTERISTIC = 2;

        final int type;
        final String description;
        final UUID uuid;

        AdapterItem(int type, String description, UUID uuid) {
            this.type = type;
            this.description = description;
            this.uuid = uuid;
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        @BindView(R.id.tv_characteristic)
        TextView tv_characteristic;
        @BindView(R.id.tv_uuid)
        TextView tv_uuid;
        @BindView(R.id.tv_uuid_hex)
        TextView tv_uuid_hex;
        @BindView(R.id.child_image)
        ImageView iv_child_image;

        ViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }
    }

    interface OnAdapterItemClickListener {

        void onAdapterViewClick(View view);
    }

    private final List<AdapterItem> data = new ArrayList<>();
    private OnAdapterItemClickListener onAdapterItemClickListener;
    private final View.OnClickListener onClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {

            if (onAdapterItemClickListener != null) {
                onAdapterItemClickListener.onAdapterViewClick(v);
            }
        }
    };

    @Override
    public int getItemCount() {
        return data.size();
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).type;
    }

    @SuppressLint({"SetTextI18n", "UseCompatLoadingForDrawables"})
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final int itemViewType = holder.getItemViewType();
        final AdapterItem item = getItem(position);

        if (FULL_ECG_SERVICE_UUID.compareTo(item.uuid) == 0) {
            if (itemViewType == AdapterItem.SERVICE) {
                holder.tv_uuid.setText(String.format("Service: %s", item.description));
            } else {
                holder.tv_uuid.setText(String.format("Characteristic: %s", item.description));
            }
            holder.tv_characteristic.setText("Full ECG Service");
            holder.tv_uuid_hex.setText("0xba55");
        } else if (FULL_ECG_NUM_LEAD.compareTo(item.uuid) == 0) {
            if (itemViewType == AdapterItem.SERVICE) {
                holder.tv_uuid.setText(String.format("Service: %s", item.description));
            } else {
                holder.tv_uuid.setText(String.format("Characteristic: %s", item.description));
            }
            holder.tv_characteristic.setText("Number of Active Leads");
            holder.tv_uuid_hex.setText("0x3BAD");
            holder.iv_child_image.setImageDrawable(mContext.getResources().getDrawable(R.drawable.icon_ecg_lead));
        } else if (FULL_ECG_STREAM_FLAG.compareTo(item.uuid) == 0) {
            if (itemViewType == AdapterItem.SERVICE) {
                holder.tv_uuid.setText(String.format("Service: %s", item.description));
            } else {
                holder.tv_uuid.setText(String.format("Characteristic: %s", item.description));
            }
            holder.tv_characteristic.setText("Stream Enabled Flag");
            holder.tv_uuid_hex.setText("0x4BAD");
            holder.iv_child_image.setImageDrawable(mContext.getResources().getDrawable(R.drawable.icon_live_stream));
        } else if (FULL_ECG_STREAM_DATA.compareTo(item.uuid) == 0) {
            if (itemViewType == AdapterItem.SERVICE) {
                holder.tv_uuid.setText(String.format("Service: %s", item.description));
            } else {
                holder.tv_uuid.setText(String.format("Characteristic: %s", item.description));
            }
            holder.tv_characteristic.setText("Stream Data");
            holder.tv_uuid_hex.setText("0x2BAD");
            holder.iv_child_image.setImageDrawable(mContext.getResources().getDrawable(R.drawable.icon_ecg_data));
        } else if (FULL_ECG_ACQ_FLAG.compareTo(item.uuid) == 0) {
            if (itemViewType == AdapterItem.SERVICE) {
                holder.tv_uuid.setText(String.format("Service: %s", item.description));
            } else {
                holder.tv_uuid.setText(String.format("Characteristic: %s", item.description));
            }
            holder.tv_characteristic.setText("ACQ Enabled");
            holder.tv_uuid_hex.setText("0x5BAD");
            holder.iv_child_image.setImageDrawable(mContext.getResources().getDrawable(R.drawable.icon_acq_data));
        }

        else if (FULL_ONE_ECG_SERVICE_UUID.compareTo(item.uuid) == 0) {
            if (itemViewType == AdapterItem.SERVICE) {
                holder.tv_uuid.setText(String.format("Service: %s", item.description));
            } else {
                holder.tv_uuid.setText(String.format("Characteristic: %s", item.description));
            }
            holder.tv_characteristic.setText("One ECG Lead Service");
            holder.tv_uuid_hex.setText("0xbc66");
        } else if (FULL_ONE_ECG_NUM_LEAD.compareTo(item.uuid) == 0) {
            if (itemViewType == AdapterItem.SERVICE) {
                holder.tv_uuid.setText(String.format("Service: %s", item.description));
            } else {
                holder.tv_uuid.setText(String.format("Characteristic: %s", item.description));
            }
            holder.tv_characteristic.setText("Number of Active Leads");
            holder.tv_uuid_hex.setText("0x3BAD");
            holder.iv_child_image.setImageDrawable(mContext.getResources().getDrawable(R.drawable.icon_ecg_lead));
        } else if (FULL_ONE_ECG_STREAM_FLAG.compareTo(item.uuid) == 0) {
            if (itemViewType == AdapterItem.SERVICE) {
                holder.tv_uuid.setText(String.format("Service: %s", item.description));
            } else {
                holder.tv_uuid.setText(String.format("Characteristic: %s", item.description));
            }
            holder.tv_characteristic.setText("Stream Enabled Flag 1-Lead");
            holder.tv_uuid_hex.setText("0x4BCD");
            holder.iv_child_image.setImageDrawable(mContext.getResources().getDrawable(R.drawable.icon_live_stream));
        } else if (FULL_ONE_ECG_STREAM_DATA.compareTo(item.uuid) == 0) {
            if (itemViewType == AdapterItem.SERVICE) {
                holder.tv_uuid.setText(String.format("Service: %s", item.description));
            } else {
                holder.tv_uuid.setText(String.format("Characteristic: %s", item.description));
            }
            holder.tv_characteristic.setText("Stream Data 1-Lead");
            holder.tv_uuid_hex.setText("0x2BCD");
            holder.iv_child_image.setImageDrawable(mContext.getResources().getDrawable(R.drawable.icon_ecg_data));
        } else if (FULL_ONE_ECG_ACQ_FLAG.compareTo(item.uuid) == 0) {
            if (itemViewType == AdapterItem.SERVICE) {
                holder.tv_uuid.setText(String.format("Service: %s", item.description));
            } else {
                holder.tv_uuid.setText(String.format("Characteristic: %s", item.description));
            }
            holder.tv_characteristic.setText("ACQ Enabled 1-Lead");
            holder.tv_uuid_hex.setText("0x5BCD");
            holder.iv_child_image.setImageDrawable(mContext.getResources().getDrawable(R.drawable.icon_acq_data));
        }

        else if (FULL_THREE_ECG_SERVICE_UUID.compareTo(item.uuid) == 0) {
            if (itemViewType == AdapterItem.SERVICE) {
                holder.tv_uuid.setText(String.format("Service: %s", item.description));
            } else {
                holder.tv_uuid.setText(String.format("Characteristic: %s", item.description));
            }
            holder.tv_characteristic.setText("Three ECG Lead Service");
            holder.tv_uuid_hex.setText("0xbd77");
        } else if (FULL_THREE_ECG_NUM_LEAD.compareTo(item.uuid) == 0) {
            if (itemViewType == AdapterItem.SERVICE) {
                holder.tv_uuid.setText(String.format("Service: %s", item.description));
            } else {
                holder.tv_uuid.setText(String.format("Characteristic: %s", item.description));
            }
            holder.tv_characteristic.setText("Number of Active Leads");
            holder.tv_uuid_hex.setText("0x3BDD");
            holder.iv_child_image.setImageDrawable(mContext.getResources().getDrawable(R.drawable.icon_ecg_lead));
        } else if (FULL_THREE_ECG_STREAM_FLAG.compareTo(item.uuid) == 0) {
            if (itemViewType == AdapterItem.SERVICE) {
                holder.tv_uuid.setText(String.format("Service: %s", item.description));
            } else {
                holder.tv_uuid.setText(String.format("Characteristic: %s", item.description));
            }
            holder.tv_characteristic.setText("Stream Enabled Flag 3-Lead");
            holder.tv_uuid_hex.setText("0x4BDD");
            holder.iv_child_image.setImageDrawable(mContext.getResources().getDrawable(R.drawable.icon_live_stream));
        } else if (FULL_THREE_ECG_STREAM_DATA.compareTo(item.uuid) == 0) {
            if (itemViewType == AdapterItem.SERVICE) {
                holder.tv_uuid.setText(String.format("Service: %s", item.description));
            } else {
                holder.tv_uuid.setText(String.format("Characteristic: %s", item.description));
            }
            holder.tv_characteristic.setText("Stream Data 3-Lead");
            holder.tv_uuid_hex.setText("0x2BDD");
            holder.iv_child_image.setImageDrawable(mContext.getResources().getDrawable(R.drawable.icon_ecg_data));
        } else if (FULL_THREE_ECG_ACQ_FLAG.compareTo(item.uuid) == 0) {
            if (itemViewType == AdapterItem.SERVICE) {
                holder.tv_uuid.setText(String.format("Service: %s", item.description));
            } else {
                holder.tv_uuid.setText(String.format("Characteristic: %s", item.description));
            }
            holder.tv_characteristic.setText("ACQ Enabled 3-Lead");
            holder.tv_uuid_hex.setText("0x5BDD");
            holder.iv_child_image.setImageDrawable(mContext.getResources().getDrawable(R.drawable.icon_acq_data));
        }
    }

    @Override
    @NonNull
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final int layout = viewType == AdapterItem.SERVICE ? R.layout.item2_discovery_service : R.layout.item2_discovery_characteristic;
        final View itemView = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        itemView.setOnClickListener(onClickListener);
        return new ViewHolder(itemView);
    }

    void setOnAdapterItemClickListener(OnAdapterItemClickListener onAdapterItemClickListener) {
        this.onAdapterItemClickListener = onAdapterItemClickListener;
    }

    void swapScanResult(RxBleDeviceServices services) {
        data.clear();

        for (BluetoothGattService service : services.getBluetoothGattServices()) {
            // Add service
            if (FULL_ECG_SERVICE_UUID.compareTo(service.getUuid()) == 0) {
                data.add(new AdapterItem(AdapterItem.SERVICE, getServiceType(service), service.getUuid()));
                final List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();

                for (BluetoothGattCharacteristic characteristic : characteristics) {
                    data.add(new AdapterItem(AdapterItem.CHARACTERISTIC, describeProperties(characteristic), characteristic.getUuid()));
                    Log.i("Characteristic UUID", characteristic.getUuid().toString());
                }
            }
            if (FULL_ONE_ECG_SERVICE_UUID.compareTo(service.getUuid()) == 0) {
                data.add(new AdapterItem(AdapterItem.SERVICE, getServiceType(service), service.getUuid()));
                final List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();

                for (BluetoothGattCharacteristic characteristic : characteristics) {
                    data.add(new AdapterItem(AdapterItem.CHARACTERISTIC, describeProperties(characteristic), characteristic.getUuid()));
                    Log.i("Characteristic UUID", characteristic.getUuid().toString());
                }
            }
            if (FULL_THREE_ECG_SERVICE_UUID.compareTo(service.getUuid()) == 0) {
                data.add(new AdapterItem(AdapterItem.SERVICE, getServiceType(service), service.getUuid()));

                final List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();

                for (BluetoothGattCharacteristic characteristic : characteristics) {
                    data.add(new AdapterItem(AdapterItem.CHARACTERISTIC, describeProperties(characteristic), characteristic.getUuid()));
                    Log.i("Characteristic UUID", characteristic.getUuid().toString());
                }
            }
        }

        notifyDataSetChanged();
    }

    public void clearData() {
        data.clear();
    }

    private String describeProperties(BluetoothGattCharacteristic characteristic) {
        List<String> properties = new ArrayList<>();
        if (isCharacteristicReadable(characteristic)) properties.add("Read");
        if (isCharacteristicWriteable(characteristic)) properties.add("Write");
        if (isCharacteristicNotifiable(characteristic)) properties.add("Notify");
        return TextUtils.join(" ", properties);
    }

    AdapterItem getItem(int position) {
        return data.get(position);
    }

    private String getServiceType(BluetoothGattService service) {
        return service.getType() == BluetoothGattService.SERVICE_TYPE_PRIMARY ? "primary" : "secondary";
    }

    private boolean isCharacteristicNotifiable(BluetoothGattCharacteristic characteristic) {
        return (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0;
    }

    private boolean isCharacteristicReadable(BluetoothGattCharacteristic characteristic) {
        return ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) != 0);
    }

    private boolean isCharacteristicWriteable(BluetoothGattCharacteristic characteristic) {
        return (characteristic.getProperties() & (BluetoothGattCharacteristic.PROPERTY_WRITE
                | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0;
    }
}
