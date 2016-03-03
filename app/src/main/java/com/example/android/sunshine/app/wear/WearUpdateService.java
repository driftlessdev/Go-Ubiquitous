package com.example.android.sunshine.app.wear;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;

import com.example.android.sunshine.app.Utility;
import com.example.android.sunshine.app.data.WeatherContract;
import com.example.android.sunshine.app.sync.SunshineSyncAdapter;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.ByteArrayOutputStream;
import java.util.Calendar;

/**
 * Created by Tim on 3/2/2016.
 */
public class WearUpdateService extends IntentService
        implements GoogleApiClient.ConnectionCallbacks
        ,GoogleApiClient.OnConnectionFailedListener
{
    private static final String LOG_TAG = WearUpdateService.class.getSimpleName();
    private GoogleApiClient mGoogleApiClient;
    public static final String PATH_WEATHER_REQUEST = "/WearUpdateService/Request";
    public static final String PATH_WEATHER_REPLY = "/WearUpdateService/Reply";
    public static final String PATH_WEATHER_UPDATE = "/WearUpdateService/Update";
    public static final String KEY_TODAY_HIGH = "HighToday";
    public static final String KEY_TODAY_LOW = "LowToday";
    public static final String KEY_TODAY_COND = "CondToday";
    public static final String KEY_TODAY_ICON = "IconToday";

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

    public WearUpdateService()
    {
        super(LOG_TAG);
    }

    @Override
    public void onDestroy() {
        mGoogleApiClient.disconnect();
        super.onDestroy();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mGoogleApiClient = new GoogleApiClient.Builder(getApplicationContext())
                .addOnConnectionFailedListener(this)
                .addConnectionCallbacks(this)
                .addApi(Wearable.API)
                .build();
        mGoogleApiClient.connect();
    }

    protected void onHandleIntent(Intent intent) {
        boolean dataUpdated = intent != null &&
                SunshineSyncAdapter.ACTION_DATA_UPDATED.equals(intent.getAction());

        Log.d(LOG_TAG, "onHandleIntent: " + dataUpdated);
        if(dataUpdated)
        {
            sendWeatherUpdate();
        }
    }

    public static Intent createIntent(Context context)
    {
        return new Intent(SunshineSyncAdapter.ACTION_DATA_UPDATED)
                .setClass(context, WearUpdateService.class);
    }

    @Override
    public void onConnected(Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    private void sendWeatherUpdate()
    {
        Log.d(LOG_TAG,"sendWeatherUpdate");
        Context context = getApplicationContext();
        String locationSetting = Utility.getPreferredLocation(context);

        Cursor data = context.getContentResolver().query(
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
        int defaultImage = Utility.getIconResourceForWeatherCondition(weatherId);
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), defaultImage);
        Asset asset = createAssetFromBitmap(bitmap);

        double high = data.getDouble(COL_WEATHER_MAX_TEMP);
        String highString = Utility.formatTemperature(context, high);
        double low = data.getDouble(COL_WEATHER_MIN_TEMP);
        String lowString = Utility.formatTemperature(context, low);

        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(PATH_WEATHER_UPDATE);
        DataMap weather = putDataMapRequest.getDataMap();

        weather.putString(KEY_TODAY_HIGH, highString);
        weather.putString(KEY_TODAY_LOW, lowString);
        weather.putAsset(KEY_TODAY_ICON, asset);
        weather.putInt(KEY_TODAY_COND, weatherId);

        PutDataRequest putDataRequest = putDataMapRequest.asPutDataRequest();
        com.google.android.gms.common.api.PendingResult<DataApi.DataItemResult> pendingResult = Wearable.DataApi.putDataItem(mGoogleApiClient, putDataRequest);
        DataApi.DataItemResult dataItemResult = pendingResult.await();
        if (dataItemResult.getStatus().isSuccess()) {
            Log.d(LOG_TAG, "Data sent OK");
        } else {
            Log.e(LOG_TAG, "Data failed: " + dataItemResult.toString());
        }
    }

    private static Asset createAssetFromBitmap(Bitmap bitmap) {
        final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream);
        return Asset.createFromBytes(byteStream.toByteArray());
    }
}
