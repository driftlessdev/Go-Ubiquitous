package com.example.android.sunshine.app.wear;

import android.content.Intent;
import android.util.Log;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * Created by Tim on 3/2/2016.
 */
public class SunshineWearListenerService extends WearableListenerService
{
    private static final String LOG_TAG = SunshineWearListenerService.class.getSimpleName();
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.d(LOG_TAG, "onMessageReceived: " + messageEvent.getPath());
        if(messageEvent.getPath().equals(WearUpdateService.PATH_WEATHER_REQUEST))
        {
            Intent serviceIntent = WearUpdateService.createIntent(getApplicationContext());
            getApplicationContext().startService(serviceIntent);
        }
    }
}

