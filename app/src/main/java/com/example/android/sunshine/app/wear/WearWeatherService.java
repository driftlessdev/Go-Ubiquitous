package com.example.android.sunshine.app.wear;

import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.Random;

public class WearWeatherService extends WearableListenerService {
    private static final String LOG_TAG = WearWeatherService.class.getSimpleName();
    public static final String PATH_WEATHER_REQUEST = "/WearWeatherService/Request";
    public static final String PATH_WEATHER_REPLY = "/WearWeatherService/Reply";
    public static final String KEY_TODAY_HIGH = "HighToday";
    public static final String KEY_TODAY_LOW = "LowToday";
    public static final String KEY_TODAY_COND = "CondToday";


    private String mPeerId;
    private GoogleApiClient mGoogleApiClient;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(LOG_TAG, "onCreate");
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();
        mGoogleApiClient.connect();
    }

    public WearWeatherService() {
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);
        mPeerId = messageEvent.getSourceNodeId();
        Log.d(LOG_TAG, "onMessageReceived: " + messageEvent.getPath());
        if(messageEvent.getPath().equals(PATH_WEATHER_REQUEST))
        {
            Log.d(LOG_TAG, "START WEATHER REQUEST");
            DataMap weather = new DataMap();
            Random random = new Random();
            weather.putDouble(KEY_TODAY_HIGH, random.nextDouble());
            weather.putDouble(KEY_TODAY_LOW, random.nextDouble());
            weather.putString(KEY_TODAY_COND, "ACK!");
            Wearable.MessageApi.sendMessage(mGoogleApiClient, mPeerId, PATH_WEATHER_REPLY, weather.toByteArray());
        }
    }
}
