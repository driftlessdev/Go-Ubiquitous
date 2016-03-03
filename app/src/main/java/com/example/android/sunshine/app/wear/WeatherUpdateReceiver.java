package com.example.android.sunshine.app.wear;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Created by Tim on 2/29/2016.
 */
public class WeatherUpdateReceiver extends BroadcastReceiver
{
    private static String LOG_TAG = WeatherUpdateReceiver.class.getSimpleName();


    public void onReceive(Context context, Intent intent) {
        Log.d(LOG_TAG, "onReceive");
        Intent serviceIntent = WearUpdateService.createIntent(context);
        context.startService(serviceIntent);
    }


}
