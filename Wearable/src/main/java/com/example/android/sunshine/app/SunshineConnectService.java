package com.example.android.sunshine.app;

import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * Created by Tim on 3/2/2016.
 */
public class SunshineConnectService extends WearableListenerService {
    private static final String LOG_TAG = SunshineConnectService.class.getSimpleName();
    private GoogleApiClient mGoogleApiClient;
    public static final String PATH_WEATHER_REQUEST = "/WearUpdateService/Request";

    @Override
    public void onDestroy() {
        mGoogleApiClient.disconnect();
        super.onDestroy();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mGoogleApiClient = new GoogleApiClient.Builder(getApplicationContext())
                .addApi(Wearable.API)
                .build();
        mGoogleApiClient.connect();
    }

    @Override
    public void onPeerConnected(Node peer) {
        super.onPeerConnected(peer);
        Log.d(LOG_TAG, "requesting Weather Data");
        if(mGoogleApiClient != null && !mGoogleApiClient.isConnected())
        {
            mGoogleApiClient.connect();
        }

        Wearable.MessageApi.sendMessage(mGoogleApiClient, peer.getId(), SunshineWatchFace.PATH_WEATHER_REQUEST, null)
                .setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                    @Override
                    public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                        Log.d(LOG_TAG, "SendMessageResult: " + sendMessageResult.getStatus());
                    }
                });
    }
}
