package com.senk.bus.ui;

import android.content.Context;
import android.graphics.Typeface;

import com.itheima.wheelpicker.WheelPicker;
import com.senk.bus.R;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class WheelTimePicker {

    public interface OnTimeChangedListener {
        void onTimeChanged(int hour, int minute);
    }

    private final WheelPicker wheelHour;
    private final WheelPicker wheelMinute;
    private int hour;
    private int minute;
    private OnTimeChangedListener listener;

    public WheelTimePicker(WheelPicker wheelHour, WheelPicker wheelMinute, Context context) {
        this.wheelHour = wheelHour;
        this.wheelMinute = wheelMinute;

        List<String> hours = Arrays.asList(context.getResources().getStringArray(R.array.hours));
        List<String> minutes = Arrays.asList(context.getResources().getStringArray(R.array.minutes));
        wheelHour.setData(hours);
        wheelMinute.setData(minutes);
        wheelHour.setTypeface(Typeface.MONOSPACE);
        wheelMinute.setTypeface(Typeface.MONOSPACE);

        wheelHour.setOnItemSelectedListener((picker, data, position) -> {
            hour = position;
            notifyListener();
        });
        wheelMinute.setOnItemSelectedListener((picker, data, position) -> {
            minute = position;
            notifyListener();
        });
    }

    public void setOnTimeChangedListener(OnTimeChangedListener listener) {
        this.listener = listener;
    }

    private void notifyListener() {
        if (listener != null) {
            listener.onTimeChanged(hour, minute);
        }
    }

    public void setTime(int hour, int minute) {
        this.hour = hour;
        this.minute = minute;
        wheelHour.setSelectedItemPosition(hour);
        wheelMinute.setSelectedItemPosition(minute);
    }

    public int getHour() {
        return hour;
    }

    public int getMinute() {
        return minute;
    }

    public String getFormattedTime() {
        return String.format(Locale.getDefault(),"%02d:%02d", hour, minute);
    }
}
