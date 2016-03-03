/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
    public static final String PATH_WEATHER_REQUEST = "/WearUpdateService/Request";
    public static final String PATH_WEATHER_REPLY = "/WearUpdateService/Reply";
    public static final String PATH_WEATHER_UPDATE = "/WearUpdateService/Update";
    public static final String KEY_TODAY_HIGH = "HighToday";
    public static final String KEY_TODAY_LOW = "LowToday";
    public static final String KEY_TODAY_COND = "CondToday";
    public static final String KEY_TODAY_ICON = "IconToday";

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(30);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFace.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine
            implements
            GoogleApiClient.OnConnectionFailedListener
            ,GoogleApiClient.ConnectionCallbacks
            ,DataApi.DataListener{
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mTextPaint;
        Paint mDatePaint;
        Paint mHighPaint;
        Paint mLowPaint;
        SimpleDateFormat mTimeFormat;
        SimpleDateFormat mDateFormat;
        private GoogleApiClient mGoogleApiClient;
        private final String LOG_TAG = Engine.class.getSimpleName();

        boolean mAmbient;
        GregorianCalendar mCalendar;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getTimeZone(intent.getStringExtra("time-zone")));
            }
        };

        float mTimeYOffset;
        float mDateYOffset;
        float mHighYOffset;
        float mLowYOffset;
        float mIconSize;

        String mHigh = "00";
        String mLow = "00";
        Bitmap mIcon;
        float mBottomInsetHeight = 0;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());
            Resources resources = SunshineWatchFace.this.getResources();

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));
            mDatePaint = new Paint();
            mDatePaint = createTextPaint(resources.getColor(R.color.primary_light));
            mHighPaint = new Paint();
            mHighPaint = createTextPaint(resources.getColor(R.color.digital_text));
            mLowPaint = new Paint();
            mLowPaint = createTextPaint(resources.getColor(R.color.primary_light));
            mIconSize = getResources().getDimension(R.dimen.weather_icon_size);


            mCalendar = new GregorianCalendar();

            mTimeFormat = new SimpleDateFormat("HH:mm");
            mDateFormat = new SimpleDateFormat("EEE, MMM d yyyy");

            mGoogleApiClient = new GoogleApiClient.Builder(SunshineWatchFace.this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Wearable.API)
                    .build();
            mGoogleApiClient.connect();

            requestWeatherUpdate();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mTextPaint.setTextSize(textSize);
            Log.d(LOG_TAG, "Time Ascent: " + mTextPaint.ascent() + " Descent: " + mTextPaint.descent());
            mTimeYOffset = (mTextPaint.descent() + mTextPaint.ascent()) / 2;

            textSize = resources.getDimension(isRound ? R.dimen.date_text_size_round : R.dimen.date_text_size);
            mDatePaint.setTextSize(textSize);
            mDateYOffset = (mDatePaint.descent() + mDatePaint.ascent()) / 2;
            mHighPaint.setTextSize(textSize);
            mHighYOffset = (mHighPaint.descent() + mHighPaint.ascent()) / 2;
            mLowPaint.setTextSize(textSize);
            mLowYOffset = (mLowPaint.descent() + mLowPaint.ascent()) / 2;

            mBottomInsetHeight = insets.getSystemWindowInsetBottom();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                    mDatePaint.setAntiAlias(!inAmbientMode);
                    mLowPaint.setAntiAlias(!inAmbientMode);
                    mHighPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            int height = bounds.height();
            int width = bounds.width();

            int centerX = bounds.centerX();
            int centerY = bounds.centerY();

            int textOffset = 6;

            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            String timeText = mTimeFormat.format(mCalendar.getTime());
            float timeWidth = mTextPaint.measureText(timeText);
            float x = centerX - timeWidth / 2;
            float y = centerY - mTimeYOffset;
            canvas.drawText(timeText, x, y, mTextPaint);

            String dateText = mDateFormat.format(mCalendar.getTime());
            float dateWidth = mDatePaint.measureText(dateText);
            x = centerX - dateWidth / 2;
            y = height * 0.25f - mDateYOffset;
            canvas.drawText(dateText, x, y, mDatePaint);

            float baseY = height * 0.75f - mBottomInsetHeight;

            float tempWidth = mHighPaint.measureText(mHigh);
            x = centerX - mIconSize / 2 - tempWidth - textOffset;
            y = baseY - mHighYOffset;
            Log.d(LOG_TAG, "X: " + x + " Y: " + y);
            canvas.drawText(mHigh, x, y, mHighPaint);

            tempWidth = mLowPaint.measureText(mLow);
            x = centerX + mIconSize / 2 + textOffset;
            y = baseY - mLowYOffset;
            canvas.drawText(mLow, x, y, mLowPaint);

            if(mIcon != null)
            {
                x = centerX - mIcon.getWidth() / 2;
                y = height * 0.75f - mBottomInsetHeight - mIcon.getHeight() / 2;
                canvas.drawBitmap(mIcon, x, y, null);
            }


        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override
        public void onConnected(Bundle bundle) {
            Log.d(LOG_TAG, "onConnected");

            Wearable.DataApi.addListener(mGoogleApiClient, this);
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.d(LOG_TAG, "onConnectionSuspended: " + i);
        }

        private void requestWeatherUpdate()
        {
            Log.d(LOG_TAG, "requesting Weather Data");
            if(mGoogleApiClient != null && !mGoogleApiClient.isConnected())
            {
                mGoogleApiClient.connect();
            }

            Wearable.MessageApi.sendMessage(mGoogleApiClient, "", PATH_WEATHER_REQUEST, null)
                    .setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                        @Override
                        public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                            Log.d(LOG_TAG, "SendMessageResult: " + sendMessageResult.getStatus());
                        }
                    });
        }

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {
            Log.e(LOG_TAG, "onConnectionFailed: " + connectionResult.toString());
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            Log.d(LOG_TAG, "onDataChanged" + dataEventBuffer.toString());

            for (DataEvent event : dataEventBuffer) {
                Uri uri = event.getDataItem().getUri();
                String path = uri.getPath();
                if(PATH_WEATHER_UPDATE.equals(path))
                {
                    DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());
                    DataMap weather = dataMapItem.getDataMap();
                    mHigh = weather.getString(KEY_TODAY_HIGH);
                    mLow = weather.getString(KEY_TODAY_LOW);
                    Asset icon = weather.getAsset(KEY_TODAY_ICON);
                    loadBitmapFromAsset(icon);
                    Log.d(LOG_TAG, "High: " + weather.getString(KEY_TODAY_HIGH) + " Low: " + weather.getString(KEY_TODAY_LOW) + " Cond: " + weather.getInt(KEY_TODAY_COND));
                    invalidate();
                }
            }
        }

        private void loadBitmapFromAsset(Asset asset) {
            if (asset == null) {
                throw new IllegalArgumentException("Asset must be non-null");
            }

            if (!mGoogleApiClient.isConnected()) {
                return;
            }
            // convert asset into a file descriptor and block until it's ready
            PendingResult<DataApi.GetFdForAssetResult> pendingResult = Wearable.DataApi.getFdForAsset(mGoogleApiClient, asset);
            pendingResult.setResultCallback(new ResultCallback<DataApi.GetFdForAssetResult>() {
                @Override
                public void onResult(DataApi.GetFdForAssetResult getFdForAssetResult) {
                    if(getFdForAssetResult.getStatus().isSuccess())
                    {
                        Log.d(LOG_TAG, "Decoding asset!");
                        InputStream inputStream = getFdForAssetResult.getInputStream();
                        if(inputStream != null)
                        {
                            Bitmap icon = BitmapFactory.decodeStream(inputStream);
                            float scaleWidth = (mIconSize / icon.getHeight()) * icon.getWidth();
                            mIcon = Bitmap.createScaledBitmap(icon, (int) scaleWidth, (int) mIconSize, true);
                            invalidate();
                        }
                        else
                        {
                            Log.e(LOG_TAG, "No input stream for decoding asset");
                        }
                    }
                    else
                    {
                        Log.e(LOG_TAG, "Error getting icon from asset: " + getFdForAssetResult.getStatus().getStatusMessage());
                    }
                }
            });
        }
    }
}
