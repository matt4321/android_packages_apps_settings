/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.settings.fuelgauge;

import android.content.Intent;
import android.os.BatteryManager;
import com.android.settings.R;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.os.BatteryStats;
import android.os.SystemClock;
import android.os.BatteryStats.HistoryItem;
import android.telephony.ServiceState;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

public class BatteryHistoryChart extends View {
    static final int CHART_DATA_X_MASK = 0x0000ffff;
    static final int CHART_DATA_BIN_MASK = 0xffff0000;
    static final int CHART_DATA_BIN_SHIFT = 16;

    static class ChartData {
        int[] mColors;
        Paint[] mPaints;

        int mNumTicks;
        int[] mTicks;
        int mLastBin;

        void setColors(int[] colors) {
            mColors = colors;
            mPaints = new Paint[colors.length];
            for (int i=0; i<colors.length; i++) {
                mPaints[i] = new Paint();
                mPaints[i].setColor(colors[i]);
                mPaints[i].setStyle(Paint.Style.FILL);
            }
        }

        void init(int width) {
            if (width > 0) {
                mTicks = new int[width*2];
            } else {
                mTicks = null;
            }
            mNumTicks = 0;
            mLastBin = 0;
        }

        void addTick(int x, int bin) {
            if (bin != mLastBin && mNumTicks < mTicks.length) {
                mTicks[mNumTicks] = x | bin << CHART_DATA_BIN_SHIFT;
                mNumTicks++;
                mLastBin = bin;
            }
        }

        void finish(int width) {
            if (mLastBin != 0) {
                addTick(width, 0);
            }
        }

        void draw(Canvas canvas, int top, int height) {
            int lastBin=0, lastX=0;
            int bottom = top + height;
            for (int i=0; i<mNumTicks; i++) {
                int tick = mTicks[i];
                int x = tick&CHART_DATA_X_MASK;
                int bin = (tick&CHART_DATA_BIN_MASK) >> CHART_DATA_BIN_SHIFT;
                if (lastBin != 0) {
                    canvas.drawRect(lastX, top, x, bottom, mPaints[lastBin]);
                }
                lastBin = bin;
                lastX = x;
            }

        }
    }

    static final int SANS = 1;
    static final int SERIF = 2;
    static final int MONOSPACE = 3;

    static final int BATTERY_WARN = 29;
    static final int BATTERY_CRITICAL = 14;
    
    // First value if for phone off; first value is "scanning"; following values
    // are battery stats signal strength buckets.
    static final int NUM_PHONE_SIGNALS = 7;

    final Paint mBatteryBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint mBatteryGoodPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint mBatteryWarnPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint mBatteryCriticalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint mChargingPaint = new Paint();
    final Paint mScreenOnPaint = new Paint();
    final Paint mGpsOnPaint = new Paint();
    final Paint mWifiRunningPaint = new Paint();
    final Paint mCpuRunningPaint = new Paint();
    final ChartData mPhoneSignalChart = new ChartData();
    final TextPaint mTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    final TextPaint mHeaderTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    final Path mBatLevelPath = new Path();
    final Path mBatGoodPath = new Path();
    final Path mBatWarnPath = new Path();
    final Path mBatCriticalPath = new Path();
    final Path mChargingPath = new Path();
    final Path mScreenOnPath = new Path();
    final Path mGpsOnPath = new Path();
    final Path mWifiRunningPath = new Path();
    final Path mCpuRunningPath = new Path();
    
    BatteryStats mStats;
    Intent mBatteryBroadcast;
    long mStatsPeriod;
    String mDurationString;
    String mChargeLabelString;
    String mChargeDurationString;
    String mDrainString;
    String mChargingLabel;
    String mScreenOnLabel;
    String mGpsOnLabel;
    String mWifiRunningLabel;
    String mCpuRunningLabel;
    String mPhoneSignalLabel;
    
    int mTextAscent;
    int mTextDescent;
    int mHeaderTextAscent;
    int mHeaderTextDescent;
    int mDurationStringWidth;
    int mChargeLabelStringWidth;
    int mChargeDurationStringWidth;
    int mDrainStringWidth;

    boolean mLargeMode;

    int mLineWidth;
    int mThinLineWidth;
    int mChargingOffset;
    int mScreenOnOffset;
    int mGpsOnOffset;
    int mWifiRunningOffset;
    int mCpuRunningOffset;
    int mPhoneSignalOffset;
    int mLevelOffset;
    int mLevelTop;
    int mLevelBottom;
    static final int PHONE_SIGNAL_X_MASK = CHART_DATA_X_MASK;
    static final int PHONE_SIGNAL_BIN_MASK = CHART_DATA_BIN_MASK;
    static final int PHONE_SIGNAL_BIN_SHIFT = CHART_DATA_BIN_SHIFT;
    
    int mNumHist;
    long mHistStart;
    long mHistEnd;
    int mBatLow;
    int mBatHigh;
    boolean mHaveWifi;
    boolean mHaveGps;
    boolean mHavePhoneSignal;

    static class TextAttrs {
        ColorStateList textColor = null;
        int textSize = 15;
        int typefaceIndex = -1;
        int styleIndex = -1;

        void retrieve(Context context, TypedArray from, int index) {
            TypedArray appearance = null;
            int ap = from.getResourceId(index, -1);
            if (ap != -1) {
                appearance = context.obtainStyledAttributes(ap,
                                    com.android.internal.R.styleable.TextAppearance);
            }
            if (appearance != null) {
                int n = appearance.getIndexCount();
                for (int i = 0; i < n; i++) {
                    int attr = appearance.getIndex(i);

                    switch (attr) {
                    case com.android.internal.R.styleable.TextAppearance_textColor:
                        textColor = appearance.getColorStateList(attr);
                        break;

                    case com.android.internal.R.styleable.TextAppearance_textSize:
                        textSize = appearance.getDimensionPixelSize(attr, textSize);
                        break;

                    case com.android.internal.R.styleable.TextAppearance_typeface:
                        typefaceIndex = appearance.getInt(attr, -1);
                        break;

                    case com.android.internal.R.styleable.TextAppearance_textStyle:
                        styleIndex = appearance.getInt(attr, -1);
                        break;
                    }
                }

                appearance.recycle();
            }
        }

        void apply(Context context, TextPaint paint) {
            paint.density = context.getResources().getDisplayMetrics().density;
            paint.setCompatibilityScaling(
                    context.getResources().getCompatibilityInfo().applicationScale);

            paint.setColor(textColor.getDefaultColor());
            paint.setTextSize(textSize);

            Typeface tf = null;
            switch (typefaceIndex) {
                case SANS:
                    tf = Typeface.SANS_SERIF;
                    break;

                case SERIF:
                    tf = Typeface.SERIF;
                    break;

                case MONOSPACE:
                    tf = Typeface.MONOSPACE;
                    break;
            }

            setTypeface(paint, tf, styleIndex);
        }

        public void setTypeface(TextPaint paint, Typeface tf, int style) {
            if (style > 0) {
                if (tf == null) {
                    tf = Typeface.defaultFromStyle(style);
                } else {
                    tf = Typeface.create(tf, style);
                }

                paint.setTypeface(tf);
                // now compute what (if any) algorithmic styling is needed
                int typefaceStyle = tf != null ? tf.getStyle() : 0;
                int need = style & ~typefaceStyle;
                paint.setFakeBoldText((need & Typeface.BOLD) != 0);
                paint.setTextSkewX((need & Typeface.ITALIC) != 0 ? -0.25f : 0);
            } else {
                paint.setFakeBoldText(false);
                paint.setTextSkewX(0);
                paint.setTypeface(tf);
            }
        }
    }

    public BatteryHistoryChart(Context context, AttributeSet attrs) {
        super(context, attrs);
        
        mBatteryBackgroundPaint.setARGB(255, 128, 128, 128);
        mBatteryBackgroundPaint.setStyle(Paint.Style.FILL);
        mBatteryGoodPaint.setARGB(128, 0, 255, 0);
        mBatteryGoodPaint.setStyle(Paint.Style.STROKE);
        mBatteryWarnPaint.setARGB(128, 255, 255, 0);
        mBatteryWarnPaint.setStyle(Paint.Style.STROKE);
        mBatteryCriticalPaint.setARGB(192, 255, 0, 0);
        mBatteryCriticalPaint.setStyle(Paint.Style.STROKE);
        mChargingPaint.setARGB(255, 0, 128, 0);
        mChargingPaint.setStyle(Paint.Style.STROKE);
        mScreenOnPaint.setStyle(Paint.Style.STROKE);
        mGpsOnPaint.setStyle(Paint.Style.STROKE);
        mWifiRunningPaint.setStyle(Paint.Style.STROKE);
        mCpuRunningPaint.setStyle(Paint.Style.STROKE);
        mPhoneSignalChart.setColors(new int[] {
                0x00000000, 0xffa00000, 0xffa07000, 0xffa0a000,
                0xff80a000, 0xff409000, 0xff008000
        });
        
        TypedArray a =
            context.obtainStyledAttributes(
                attrs, R.styleable.BatteryHistoryChart, 0, 0);

        final TextAttrs mainTextAttrs = new TextAttrs();
        final TextAttrs headTextAttrs = new TextAttrs();
        mainTextAttrs.retrieve(context, a, R.styleable.BatteryHistoryChart_android_textAppearance);
        headTextAttrs.retrieve(context, a, R.styleable.BatteryHistoryChart_headerAppearance);

        int shadowcolor = 0;
        float dx=0, dy=0, r=0;
        
        int n = a.getIndexCount();
        for (int i = 0; i < n; i++) {
            int attr = a.getIndex(i);

            switch (attr) {
                case R.styleable.BatteryHistoryChart_android_shadowColor:
                    shadowcolor = a.getInt(attr, 0);
                    break;

                case R.styleable.BatteryHistoryChart_android_shadowDx:
                    dx = a.getFloat(attr, 0);
                    break;

                case R.styleable.BatteryHistoryChart_android_shadowDy:
                    dy = a.getFloat(attr, 0);
                    break;

                case R.styleable.BatteryHistoryChart_android_shadowRadius:
                    r = a.getFloat(attr, 0);
                    break;

                case R.styleable.BatteryHistoryChart_android_textColor:
                    mainTextAttrs.textColor = a.getColorStateList(attr);
                    headTextAttrs.textColor = a.getColorStateList(attr);
                    break;

                case R.styleable.BatteryHistoryChart_android_textSize:
                    mainTextAttrs.textSize = a.getDimensionPixelSize(attr, mainTextAttrs.textSize);
                    headTextAttrs.textSize = a.getDimensionPixelSize(attr, headTextAttrs.textSize);
                    break;

                case R.styleable.BatteryHistoryChart_android_typeface:
                    mainTextAttrs.typefaceIndex = a.getInt(attr, mainTextAttrs.typefaceIndex);
                    headTextAttrs.typefaceIndex = a.getInt(attr, headTextAttrs.typefaceIndex);
                    break;

                case R.styleable.BatteryHistoryChart_android_textStyle:
                    mainTextAttrs.styleIndex = a.getInt(attr, mainTextAttrs.styleIndex);
                    headTextAttrs.styleIndex = a.getInt(attr, headTextAttrs.styleIndex);
                    break;
            }
        }
        
        a.recycle();
        
        mainTextAttrs.apply(context, mTextPaint);
        headTextAttrs.apply(context, mHeaderTextPaint);

        if (shadowcolor != 0) {
            mTextPaint.setShadowLayer(r, dx, dy, shadowcolor);
            mHeaderTextPaint.setShadowLayer(r, dx, dy, shadowcolor);
        }
    }
    
    void setStats(BatteryStats stats, Intent broadcast) {
        mStats = stats;
        mBatteryBroadcast = broadcast;

        final long elapsedRealtimeUs = SystemClock.elapsedRealtime() * 1000;

        long uSecTime = mStats.computeBatteryRealtime(elapsedRealtimeUs,
                BatteryStats.STATS_SINCE_CHARGED);
        mStatsPeriod = uSecTime;
        mChargingLabel = getContext().getString(R.string.battery_stats_charging_label);
        mScreenOnLabel = getContext().getString(R.string.battery_stats_screen_on_label);
        mGpsOnLabel = getContext().getString(R.string.battery_stats_gps_on_label);
        mWifiRunningLabel = getContext().getString(R.string.battery_stats_wifi_running_label);
        mCpuRunningLabel = getContext().getString(R.string.battery_stats_wake_lock_label);
        mPhoneSignalLabel = getContext().getString(R.string.battery_stats_phone_signal_label);
        
        int pos = 0;
        int lastInteresting = 0;
        byte lastLevel = -1;
        mBatLow = 0;
        mBatHigh = 100;
        int aggrStates = 0;
        boolean first = true;
        if (stats.startIteratingHistoryLocked()) {
            final HistoryItem rec = new HistoryItem();
            while (stats.getNextHistoryLocked(rec)) {
                pos++;
                if (rec.isDeltaData()) {
                    if (first) {
                        first = false;
                        mHistStart = rec.time;
                    }
                    if (rec.batteryLevel != lastLevel || pos == 1) {
                        lastLevel = rec.batteryLevel;
                    }
                    lastInteresting = pos;
                    mHistEnd = rec.time;
                    aggrStates |= rec.states;
                }
            }
        }
        mNumHist = lastInteresting;
        mHaveGps = (aggrStates&HistoryItem.STATE_GPS_ON_FLAG) != 0;
        mHaveWifi = (aggrStates&HistoryItem.STATE_WIFI_RUNNING_FLAG) != 0;
        if (!com.android.settings.Utils.isWifiOnly(getContext())) {
            mHavePhoneSignal = true;
        }
        if (mHistEnd <= mHistStart) mHistEnd = mHistStart+1;

        //String durationString = Utils.formatElapsedTime(getContext(), mStatsPeriod / 1000, true);
        //mDurationString = getContext().getString(R.string.battery_stats_on_battery,
        //        durationString);
        mDurationString = Utils.formatElapsedTime(getContext(), mHistEnd - mHistStart, true);
        mDrainString = com.android.settings.Utils.getBatteryPercentage(mBatteryBroadcast);
        mChargeLabelString = com.android.settings.Utils.getBatteryStatus(getResources(),
                mBatteryBroadcast);
        final long drainTime = mStats.computeBatteryTimeRemaining(elapsedRealtimeUs);
        final long chargeTime = mStats.computeChargeTimeRemaining(elapsedRealtimeUs);
        final int status = mBatteryBroadcast.getIntExtra(BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN);
        if (drainTime > 0) {
            String timeString = Utils.formatShortElapsedTime(getContext(),drainTime / 1000);
            mChargeDurationString = getContext().getResources().getString(
                    R.string.power_discharge_remaining, timeString);
        } else if (chargeTime > 0 && status != BatteryManager.BATTERY_STATUS_FULL) {
            String timeString = Utils.formatShortElapsedTime(getContext(), chargeTime / 1000);
            mChargeDurationString = getContext().getResources().getString(
                    R.string.power_charge_remaining, timeString);
        } else {
            mChargeDurationString = "";
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        mDurationStringWidth = (int)mTextPaint.measureText(mDurationString);
        mDrainStringWidth = (int)mHeaderTextPaint.measureText(mDrainString);
        mChargeLabelStringWidth = (int)mHeaderTextPaint.measureText(mChargeLabelString);
        mChargeDurationStringWidth = (int)mHeaderTextPaint.measureText(mChargeDurationString);
        mTextAscent = (int)mTextPaint.ascent();
        mTextDescent = (int)mTextPaint.descent();
        mHeaderTextAscent = (int)mHeaderTextPaint.ascent();
        mHeaderTextDescent = (int)mHeaderTextPaint.descent();
    }

    void finishPaths(int w, int h, int levelh, int startX, int y, Path curLevelPath,
            int lastX, boolean lastCharging, boolean lastScreenOn, boolean lastGpsOn,
            boolean lastWifiRunning, boolean lastCpuRunning, Path lastPath) {
        if (curLevelPath != null) {
            if (lastX >= 0 && lastX < w) {
                if (lastPath != null) {
                    lastPath.lineTo(w, y);
                }
                curLevelPath.lineTo(w, y);
            }
            curLevelPath.lineTo(w, mLevelTop+levelh);
            curLevelPath.lineTo(startX, mLevelTop+levelh);
            curLevelPath.close();
        }
        
        if (lastCharging) {
            mChargingPath.lineTo(w, h-mChargingOffset);
        }
        if (lastScreenOn) {
            mScreenOnPath.lineTo(w, h-mScreenOnOffset);
        }
        if (lastGpsOn) {
            mGpsOnPath.lineTo(w, h-mGpsOnOffset);
        }
        if (lastWifiRunning) {
            mWifiRunningPath.lineTo(w, h-mWifiRunningOffset);
        }
        if (lastCpuRunning) {
            mCpuRunningPath.lineTo(w, h - mCpuRunningOffset);
        }
        if (mHavePhoneSignal) {
            mPhoneSignalChart.finish(w);
        }
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        
        int textHeight = mTextDescent - mTextAscent;
        int headerTextHeight = mHeaderTextDescent - mHeaderTextAscent;
        mThinLineWidth = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                2, getResources().getDisplayMetrics());
        if (h > (textHeight*12)) {
            mLargeMode = true;
            if (h > (textHeight*15)) {
                // Plenty of room for the chart.
                mLineWidth = textHeight/2;
            } else {
                // Compress lines to make more room for chart.
                mLineWidth = textHeight/3;
            }
            mLevelTop = headerTextHeight*2 + mLineWidth;
            mScreenOnPaint.setARGB(255, 32, 64, 255);
            mGpsOnPaint.setARGB(255, 32, 64, 255);
            mWifiRunningPaint.setARGB(255, 32, 64, 255);
            mCpuRunningPaint.setARGB(255, 32, 64, 255);
        } else {
            mLargeMode = false;
            mLineWidth = mThinLineWidth;
            mLevelTop = headerTextHeight*2 + mLineWidth;
            mScreenOnPaint.setARGB(255, 0, 0, 255);
            mGpsOnPaint.setARGB(255, 0, 0, 255);
            mWifiRunningPaint.setARGB(255, 0, 0, 255);
            mCpuRunningPaint.setARGB(255, 0, 0, 255);
        }
        if (mLineWidth <= 0) mLineWidth = 1;
        mTextPaint.setStrokeWidth(mThinLineWidth);
        mBatteryGoodPaint.setStrokeWidth(mThinLineWidth);
        mBatteryWarnPaint.setStrokeWidth(mThinLineWidth);
        mBatteryCriticalPaint.setStrokeWidth(mThinLineWidth);
        mChargingPaint.setStrokeWidth(mLineWidth);
        mScreenOnPaint.setStrokeWidth(mLineWidth);
        mGpsOnPaint.setStrokeWidth(mLineWidth);
        mWifiRunningPaint.setStrokeWidth(mLineWidth);
        mCpuRunningPaint.setStrokeWidth(mLineWidth);

        if (mLargeMode) {
            int barOffset = textHeight + mLineWidth;
            mChargingOffset = mLineWidth;
            mScreenOnOffset = mChargingOffset + barOffset;
            mCpuRunningOffset = mScreenOnOffset + barOffset;
            mWifiRunningOffset = mCpuRunningOffset + barOffset;
            mGpsOnOffset = mWifiRunningOffset + (mHaveWifi ? barOffset : 0);
            mPhoneSignalOffset = mGpsOnOffset + (mHaveGps ? barOffset : 0);
            mLevelOffset = mPhoneSignalOffset + (mHavePhoneSignal ? barOffset : 0)
                    + ((mLineWidth*3)/2);
            if (mHavePhoneSignal) {
                mPhoneSignalChart.init(w);
            }
        } else {
            mScreenOnOffset = mGpsOnOffset = mWifiRunningOffset
                    = mCpuRunningOffset = mLineWidth;
            mChargingOffset = mLineWidth*2;
            mPhoneSignalOffset = 0;
            mLevelOffset = mLineWidth*3;
            if (mHavePhoneSignal) {
                mPhoneSignalChart.init(0);
            }
        }

        mBatLevelPath.reset();
        mBatGoodPath.reset();
        mBatWarnPath.reset();
        mBatCriticalPath.reset();
        mScreenOnPath.reset();
        mGpsOnPath.reset();
        mWifiRunningPath.reset();
        mCpuRunningPath.reset();
        mChargingPath.reset();
        
        final long timeStart = mHistStart;
        final long timeChange = mHistEnd-mHistStart;
        
        final int batLow = mBatLow;
        final int batChange = mBatHigh-mBatLow;
        
        final int levelh = h - mLevelOffset - mLevelTop;
        mLevelBottom = mLevelTop + levelh;
        
        int x = 0, y = 0, startX = 0, lastX = -1, lastY = -1;
        int i = 0;
        Path curLevelPath = null;
        Path lastLinePath = null;
        boolean lastCharging = false, lastScreenOn = false, lastGpsOn = false;
        boolean lastWifiRunning = false, lastCpuRunning = false;
        final int N = mNumHist;
        if (mStats.startIteratingHistoryLocked()) {
            final HistoryItem rec = new HistoryItem();
            while (mStats.getNextHistoryLocked(rec) && i < N) {
                if (rec.isDeltaData()) {
                    x = (int)(((rec.time-timeStart)*w)/timeChange);
                    y = mLevelTop + levelh - ((rec.batteryLevel-batLow)*(levelh-1))/batChange;

                    if (lastX != x) {
                        // We have moved by at least a pixel.
                        if (lastY != y) {
                            // Don't plot changes within a pixel.
                            Path path;
                            byte value = rec.batteryLevel;
                            if (value <= BATTERY_CRITICAL) path = mBatCriticalPath;
                            else if (value <= BATTERY_WARN) path = mBatWarnPath;
                            else path = mBatGoodPath;

                            if (path != lastLinePath) {
                                if (lastLinePath != null) {
                                    lastLinePath.lineTo(x, y);
                                }
                                path.moveTo(x, y);
                                lastLinePath = path;
                            } else {
                                path.lineTo(x, y);
                            }

                            if (curLevelPath == null) {
                                curLevelPath = mBatLevelPath;
                                curLevelPath.moveTo(x, y);
                                startX = x;
                            } else {
                                curLevelPath.lineTo(x, y);
                            }
                            lastX = x;
                            lastY = y;
                        }
                    }

                    final boolean charging =
                        (rec.states&HistoryItem.STATE_BATTERY_PLUGGED_FLAG) != 0;
                    if (charging != lastCharging) {
                        if (charging) {
                            mChargingPath.moveTo(x, h-mChargingOffset);
                        } else {
                            mChargingPath.lineTo(x, h-mChargingOffset);
                        }
                        lastCharging = charging;
                    }

                    final boolean screenOn =
                        (rec.states&HistoryItem.STATE_SCREEN_ON_FLAG) != 0;
                    if (screenOn != lastScreenOn) {
                        if (screenOn) {
                            mScreenOnPath.moveTo(x, h-mScreenOnOffset);
                        } else {
                            mScreenOnPath.lineTo(x, h-mScreenOnOffset);
                        }
                        lastScreenOn = screenOn;
                    }

                    final boolean gpsOn =
                        (rec.states&HistoryItem.STATE_GPS_ON_FLAG) != 0;
                    if (gpsOn != lastGpsOn) {
                        if (gpsOn) {
                            mGpsOnPath.moveTo(x, h-mGpsOnOffset);
                        } else {
                            mGpsOnPath.lineTo(x, h-mGpsOnOffset);
                        }
                        lastGpsOn = gpsOn;
                    }

                    final boolean wifiRunning =
                        (rec.states&HistoryItem.STATE_WIFI_RUNNING_FLAG) != 0;
                    if (wifiRunning != lastWifiRunning) {
                        if (wifiRunning) {
                            mWifiRunningPath.moveTo(x, h-mWifiRunningOffset);
                        } else {
                            mWifiRunningPath.lineTo(x, h-mWifiRunningOffset);
                        }
                        lastWifiRunning = wifiRunning;
                    }

                    final boolean cpuRunning =
                        (rec.states&HistoryItem.STATE_CPU_RUNNING_FLAG) != 0;
                    if (cpuRunning != lastCpuRunning) {
                        if (cpuRunning) {
                            mCpuRunningPath.moveTo(x, h - mCpuRunningOffset);
                        } else {
                            mCpuRunningPath.lineTo(x, h - mCpuRunningOffset);
                        }
                        lastCpuRunning = cpuRunning;
                    }

                    if (mLargeMode && mHavePhoneSignal) {
                        int bin;
                        if (((rec.states&HistoryItem.STATE_PHONE_STATE_MASK)
                                >> HistoryItem.STATE_PHONE_STATE_SHIFT)
                                == ServiceState.STATE_POWER_OFF) {
                            bin = 0;
                        } else if ((rec.states&HistoryItem.STATE_PHONE_SCANNING_FLAG) != 0) {
                            bin = 1;
                        } else {
                            bin = (rec.states&HistoryItem.STATE_SIGNAL_STRENGTH_MASK)
                                    >> HistoryItem.STATE_SIGNAL_STRENGTH_SHIFT;
                            bin += 2;
                        }
                        mPhoneSignalChart.addTick(x, bin);
                    }

                } else if (rec.cmd != BatteryStats.HistoryItem.CMD_OVERFLOW) {
                    if (curLevelPath != null) {
                        finishPaths(x+1, h, levelh, startX, lastY, curLevelPath, lastX,
                                lastCharging, lastScreenOn, lastGpsOn, lastWifiRunning,
                                lastCpuRunning, lastLinePath);
                        lastX = lastY = -1;
                        curLevelPath = null;
                        lastLinePath = null;
                        lastCharging = lastScreenOn = lastGpsOn = lastCpuRunning = false;
                    }
                }
                
                i++;
            }
        }
        
        finishPaths(w, h, levelh, startX, lastY, curLevelPath, lastX,
                lastCharging, lastScreenOn, lastGpsOn, lastWifiRunning,
                lastCpuRunning, lastLinePath);
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        final int width = getWidth();
        final int height = getHeight();
        final boolean layoutRtl = isLayoutRtl();
        final int textStartX = layoutRtl ? width : 0;
        final int textEndX = layoutRtl ? 0 : width;
        final Paint.Align textAlignLeft = layoutRtl ? Paint.Align.RIGHT : Paint.Align.LEFT;
        final Paint.Align textAlignRight = layoutRtl ? Paint.Align.LEFT : Paint.Align.RIGHT;
        mTextPaint.setTextAlign(textAlignLeft);

        canvas.drawPath(mBatLevelPath, mBatteryBackgroundPaint);
        int durationHalfWidth = mDurationStringWidth / 2;
        if (layoutRtl) durationHalfWidth = -durationHalfWidth;
        if (mLargeMode) {
            canvas.drawText(mDurationString, (width / 2) - durationHalfWidth,
                    mLevelBottom - mTextAscent + mThinLineWidth, mTextPaint);
        } else {
            canvas.drawText(mDurationString, (width / 2) - durationHalfWidth,
                    mLevelTop + ((height-mLevelTop) / 2) - ((mTextDescent - mTextAscent) / 2)
                            - mTextAscent, mTextPaint);
        }

        int headerTop = mLevelTop/2 + (mHeaderTextDescent-mHeaderTextAscent)/2;
        mHeaderTextPaint.setTextAlign(textAlignLeft);
        canvas.drawText(mChargeLabelString, textStartX, headerTop, mHeaderTextPaint);
        durationHalfWidth = mChargeDurationStringWidth / 2;
        if (layoutRtl) durationHalfWidth = -durationHalfWidth;
        int headerCenter = ((width-mChargeDurationStringWidth-mDrainStringWidth)/2)
                + (layoutRtl ? mDrainStringWidth : mChargeLabelStringWidth);
        canvas.drawText(mChargeDurationString, headerCenter - durationHalfWidth, headerTop,
                mHeaderTextPaint);
        mHeaderTextPaint.setTextAlign(textAlignRight);
        canvas.drawText(mDrainString, textEndX, headerTop, mHeaderTextPaint);

        if (!mBatGoodPath.isEmpty()) {
            canvas.drawPath(mBatGoodPath, mBatteryGoodPaint);
        }
        if (!mBatWarnPath.isEmpty()) {
            canvas.drawPath(mBatWarnPath, mBatteryWarnPaint);
        }
        if (!mBatCriticalPath.isEmpty()) {
            canvas.drawPath(mBatCriticalPath, mBatteryCriticalPaint);
        }
        if (mHavePhoneSignal) {
            int top = height-mPhoneSignalOffset - (mLineWidth/2);
            mPhoneSignalChart.draw(canvas, top, mLineWidth);
        }
        if (!mScreenOnPath.isEmpty()) {
            canvas.drawPath(mScreenOnPath, mScreenOnPaint);
        }
        if (!mChargingPath.isEmpty()) {
            canvas.drawPath(mChargingPath, mChargingPaint);
        }
        if (mHaveGps) {
            if (!mGpsOnPath.isEmpty()) {
                canvas.drawPath(mGpsOnPath, mGpsOnPaint);
            }
        }
        if (mHaveWifi) {
            if (!mWifiRunningPath.isEmpty()) {
                canvas.drawPath(mWifiRunningPath, mWifiRunningPaint);
            }
        }
        if (!mCpuRunningPath.isEmpty()) {
            canvas.drawPath(mCpuRunningPath, mCpuRunningPaint);
        }

        if (mLargeMode) {
            if (mHavePhoneSignal) {
                canvas.drawText(mPhoneSignalLabel, textStartX,
                        height - mPhoneSignalOffset - mTextDescent, mTextPaint);
            }
            if (mHaveGps) {
                canvas.drawText(mGpsOnLabel, textStartX,
                        height - mGpsOnOffset - mTextDescent, mTextPaint);
            }
            if (mHaveWifi) {
                canvas.drawText(mWifiRunningLabel, textStartX,
                        height - mWifiRunningOffset - mTextDescent, mTextPaint);
            }
            canvas.drawText(mCpuRunningLabel, textStartX,
                    height - mCpuRunningOffset - mTextDescent, mTextPaint);
            canvas.drawText(mChargingLabel, textStartX,
                    height - mChargingOffset - mTextDescent, mTextPaint);
            canvas.drawText(mScreenOnLabel, textStartX,
                    height - mScreenOnOffset - mTextDescent, mTextPaint);
            canvas.drawLine(0, mLevelBottom+(mThinLineWidth/2), width,
                    mLevelBottom+(mThinLineWidth/2), mTextPaint);
            canvas.drawLine(0, mLevelTop, 0,
                    mLevelBottom+(mThinLineWidth/2), mTextPaint);
            for (int i=0; i<10; i++) {
                int y = mLevelTop + ((mLevelBottom-mLevelTop)*i)/10;
                canvas.drawLine(0, y, mThinLineWidth*2, y, mTextPaint);
            }
        }
    }
}
