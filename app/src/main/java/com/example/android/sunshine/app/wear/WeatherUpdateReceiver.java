package com.example.android.sunshine.app.wear;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;

import com.example.android.sunshine.app.Utility;
import com.example.android.sunshine.app.data.WeatherContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.util.Calendar;
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

    private static final String[] WEAR_COLUMNS = {
            WeatherContract.WeatherEntry.TABLE_NAME + "." + WeatherContract.WeatherEntry._ID,
            WeatherContract.WeatherEntry.COLUMN_DATE,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
    };

    public static final int COL_WEATHER_ID = 0;
    public static final int COL_WEATHER_DATE = 1;
    public static final int COL_WEATHER_MAX_TEMP = 2;
    public static final int COL_WEATHER_MIN_TEMP = 3;
    public static final int COL_WEATHER_CONDITION_ID = 4;

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

        String locationSetting = Utility.getPreferredLocation(mContext);

        Cursor data = mContext.getContentResolver().query(
                WeatherContract.WeatherEntry.buildWeatherLocationWithDate(locationSetting, Calendar.getInstance().getTimeInMillis())
                , WEAR_COLUMNS
                , null
                , null
                , null
        );

        if(data == null)
            return;

        if(!data.moveToFirst())
        {
            data.close();
            return;
        }

        int weatherId = data.getInt(COL_WEATHER_CONDITION_ID);
        double high = data.getDouble(COL_WEATHER_MAX_TEMP);
        String highString = Utility.formatTemperature(mContext, high);
        double low = data.getDouble(COL_WEATHER_MIN_TEMP);
        String lowString = Utility.formatTemperature(mContext, low);

        Random random = new Random();

        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(PATH_WEATHER_UPDATE);
        DataMap weather = putDataMapRequest.getDataMap();

        weather.putString(KEY_TODAY_HIGH, highString);
        weather.putString(KEY_TODAY_LOW, lowString);
        weather.putInt(KEY_TODAY_COND, weatherId);

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
