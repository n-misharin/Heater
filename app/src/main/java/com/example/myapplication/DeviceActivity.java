package com.example.myapplication;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.format.Time;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.io.IOException;

import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Query;


class HeaterData {
    int currentTemperature, maxTemperature, minTemperature;
    boolean isEnabled, isHeatingElementEnabled, isCoolingElementEnabled;
}

interface DataService{
    @GET("/")
    Call<HeaterData> getData();
    @GET("/")
    Call<HeaterData> getData(
            @Query("maxTemperature") int maxTemperature,
            @Query("minTemperature") int minTemperature,
            @Query("isEnabled") boolean isEnabled);
}

public class DeviceActivity extends AppCompatActivity {
    Button refreshButton, setupButton;
    TextView currentTempTextView, maxTempTextView, minTempTextView, coolingStatusTextView,
            heatingStatusTextView, refreshedStatusTextView;
    SeekBar maxTempSeekBar, minTempSeekBar;

    Switch statusControlSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.device_activity);

        currentTempTextView = findViewById(R.id.tv_curr_temp);
        maxTempTextView = findViewById(R.id.tv_max_temp);
        minTempTextView = findViewById(R.id.tv_min_temp);
        coolingStatusTextView = findViewById(R.id.tv_status_cooling);
        heatingStatusTextView = findViewById(R.id.tv_status_heating);
        refreshedStatusTextView = findViewById(R.id.tv_refreshed_status);

        maxTempSeekBar = findViewById(R.id.sb_max_temp);
        minTempSeekBar = findViewById(R.id.sb_min_temp);
        maxTempSeekBar.setOnSeekBarChangeListener(maxSeekBarChangeListener);
        minTempSeekBar.setOnSeekBarChangeListener(minSeekBarChangeListener);

        statusControlSwitch = findViewById(R.id.switch_control);

        refreshButton = findViewById(R.id.enter_button);
        setupButton = findViewById(R.id.set_settings_button);

        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshData();
            }
        });

        setupButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setupSettings();
            }
        });

        refreshData();
    }

    private void setupSettings() {
        HeaterData heaterData = new HeaterData();
        heaterData.maxTemperature = Integer.parseInt(maxTempTextView.getText().toString());
        heaterData.minTemperature = Integer.parseInt(minTempTextView.getText().toString());
        heaterData.isEnabled = statusControlSwitch.isChecked();
        Thread thread = new Thread(new AsyncQuery("192.168.4.22", heaterData));
        thread.start();
    }

    protected void refreshData(){
        Thread thread = new Thread(new AsyncQuery("192.168.4.22"));
        thread.start();
    }

    Handler handler = new Handler(Looper.getMainLooper()){
        @SuppressLint("SetTextI18n")
        @Override
        public void handleMessage(Message msg) {
            int current = msg.getData().getInt("current");
            int max = msg.getData().getInt("max");
            int min = msg.getData().getInt("min");
            boolean enabled = msg.getData().getBoolean("enabled");
            boolean heating = msg.getData().getBoolean("heating");
            boolean cooling = msg.getData().getBoolean("cooling");

            maxTempTextView.setText(String.valueOf(max));
            maxTempSeekBar.setProgress(max);
            minTempTextView.setText(String.valueOf(min));
            minTempSeekBar.setProgress(min);
            currentTempTextView.setText(String.valueOf(current));

            statusControlSwitch.setChecked(enabled);

            heatingStatusTextView.setText(
                    heating? getResources().getString(R.string.ON):
                            getResources().getString(R.string.OFF));

            coolingStatusTextView.setText(
                    cooling? getResources().getString(R.string.ON):
                            getResources().getString(R.string.OFF));

            Time time = new Time();
            time.setToNow();
            refreshedStatusTextView.setText(getResources().getText(R.string.refreshed) + " " +
                    time.format("%k:%M:%S"));
            refreshedStatusTextView.setBackgroundColor(getResources().getColor(R.color.normal_element));
        }
    };

    SeekBar.OnSeekBarChangeListener maxSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener(){

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            maxTempTextView.setText(String.valueOf(progress));
            if (progress < minTempSeekBar.getProgress()){
                minTempSeekBar.setProgress(progress);
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {

        }
    };

    SeekBar.OnSeekBarChangeListener minSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener(){

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            minTempTextView.setText(String.valueOf(progress));
            if (progress > maxTempSeekBar.getProgress()){
                maxTempSeekBar.setProgress(progress);
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {

        }
    };

    class AsyncQuery implements Runnable{
        private String host;
        private HeaterData sendHeaterData;

        public AsyncQuery(String host) {
            this(host, null);
        }

        public AsyncQuery(String host, HeaterData sendHeaterData) {
            this.host = host;
            this.sendHeaterData = sendHeaterData;
        }

        @Override
        public void run() {
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl("http://" + this.host)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            DataService dataService = retrofit.create(DataService.class);
            try {
                Call<HeaterData> call;
                if (this.sendHeaterData == null){
                    call = dataService.getData();
                }else{
                    call = dataService.getData(
                            this.sendHeaterData.maxTemperature,
                            this.sendHeaterData.minTemperature,
                            this.sendHeaterData.isEnabled);
                }
                Response<HeaterData> response = call.execute();

                Bundle bundle = new Bundle();
                bundle.putInt("current", response.body().currentTemperature);
                bundle.putInt("max", response.body().maxTemperature);
                bundle.putInt("min", response.body().minTemperature);
                bundle.putBoolean("enabled", response.body().isEnabled);
                bundle.putBoolean("heating", response.body().isHeatingElementEnabled);
                bundle.putBoolean("cooling", response.body().isCoolingElementEnabled);

                Message message = new Message();
                message.setData(bundle);
                handler.sendMessage(message);
            } catch (IOException e) {
                refreshedStatusTextView.setText(getResources().getText(R.string.device_not_connected));
                refreshedStatusTextView.setBackgroundColor(getResources().getColor(R.color.hot_element));
                e.printStackTrace();
            }
        }
    }
}