package com.example.android.sunshine.app.wear;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.util.Random;

/**
 * Created by Tim on 2/29/2016.
 */
public class WeatherUpdateReceiver extends BroadcastReceiver
    implements GoogleApiClient.ConnectionCallbacks
    ,GoogleApiClient.OnConnectionFailedListener
{
    private static String LOG_TAG = WeatherUpdateReceiver.class.getSimpleName();
    private GoogleApiClient mGoogleApiClient;
    private Context mContext;
    public static final String PATH_WEATHER_REQUEST = "/WearWeatherService/Request";
    public static final String PATH_WEATHER_REPLY = "/WearWeatherService/Reply";
    public static final String PATH_WEATHER_UPDATE = "/WearWeatherService/Update";
    public static final String KEY_TODAY_HIGH = "HighToday";
    public static final String KEY_TODAY_LOW = "LowToday";
    public static final String KEY_TODAY_COND = "CondToday";
    @Override
    public void onConnected(Bundle bundle) {
        sendWeatherUpdate();
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    public void onReceive(Context context, Intent intent) {
        Log.d(LOG_TAG, "onReceive");
        mContext = context;
        mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addOnConnectionFailedListener(this)
                .addConnectionCallbacks(this)
                .addApi(Wearable.API)
                .build();
        mGoogleApiClient.connect();
    }

    private void sendWeatherUpdate()
    {
        Random random = new Random();

        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(PATH_WEATHER_UPDATE);
        DataMap weather = putDataMapRequest.getDataMap();

        Log.d(LOG_TAG, "Previous data: High: " + weather.getDouble(KEY_TODAY_HIGH) + " Low: " + weather.getDouble(KEY_TODAY_LOW) + " Cond: " + weather.getString(KEY_TODAY_COND));

        weather.putDouble(KEY_TODAY_HIGH, 42.2);
        weather.putDouble(KEY_TODAY_LOW, 20.7);
        weather.putString(KEY_TODAY_COND, "ACK!");

        PutDataRequest putDataRequest = putDataMapRequest.asPutDataRequest();
        com.google.android.gms.common.api.PendingResult<DataApi.DataItemResult> pendingResult = Wearable.DataApi.putDataItem(mGoogleApiClient, putDataRequest);
        pendingResult.setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
            @Override
            public void onResult(DataApi.DataItemResult dataItemResult) {
                if(dataItemResult.getStatus().isSuccess())
                {
                    Log.d(LOG_TAG, "Data sent OK");
                }
                else
                {
                    Log.e(LOG_TAG, "Data failed: " + dataItemResult.toString());
                }
            }
        });
    }
}
