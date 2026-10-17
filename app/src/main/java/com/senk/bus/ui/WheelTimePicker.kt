package com.senk.bus.ui

import android.content.Context
import android.graphics.Typeface
import com.itheima.wheelpicker.WheelPicker
import com.senk.bus.R
import java.util.Locale

class WheelTimePicker(
    private val wheelHour: WheelPicker,
    private val wheelMinute: WheelPicker,
    context: Context,
) {

    fun interface OnTimeChangedListener {
        fun onTimeChanged(hour: Int, minute: Int)
    }

    private var hourValue: Int = 0
    private var minuteValue: Int = 0
    private var listener: OnTimeChangedListener? = null

    init {
        val hours: List<String> = context.resources.getStringArray(R.array.hours).toList()
        val minutes: List<String> = context.resources.getStringArray(R.array.minutes).toList()
        wheelHour.setData(hours)
        wheelMinute.setData(minutes)
        wheelHour.setTypeface(Typeface.MONOSPACE)
        wheelMinute.setTypeface(Typeface.MONOSPACE)

        wheelHour.setOnItemSelectedListener { _, _, position ->
            hourValue = position
            notifyListener()
        }
        wheelMinute.setOnItemSelectedListener { _, _, position ->
            minuteValue = position
            notifyListener()
        }
    }

    fun setOnTimeChangedListener(listener: OnTimeChangedListener?) {
        this.listener = listener
    }

    private fun notifyListener() {
        listener?.onTimeChanged(hourValue, minuteValue)
    }

    fun setTime(hour: Int, minute: Int) {
        hourValue = hour
        minuteValue = minute
        wheelHour.setSelectedItemPosition(hour)
        wheelMinute.setSelectedItemPosition(minute)
    }

    fun getHour(): Int = hourValue

    fun getMinute(): Int = minuteValue

    fun getFormattedTime(): String =
        String.format(Locale.ROOT, "%02d:%02d", hourValue, minuteValue)
}
