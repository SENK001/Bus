package com.senk.bus.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.senk.bus.R;
import com.senk.bus.data.entity.Schedule;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ScheduleAdapter extends RecyclerView.Adapter<ScheduleAdapter.ViewHolder> {

    private List<Schedule> schedules = new ArrayList<>();
    private int nextIndex = -1;
    private OnScheduleClickListener clickListener;
    private OnScheduleLongClickListener longClickListener;

    public interface OnScheduleClickListener {
        void onScheduleClick(Schedule schedule);
    }

    public interface OnScheduleLongClickListener {
        void onScheduleLongClick(Schedule schedule, View anchor);
    }

    public void setSchedules(List<Schedule> schedules) {
        this.schedules = schedules;
        updateNextIndex();
        notifyDataSetChanged();
    }

    public List<Schedule> getCurrentSchedules() {
        return schedules;
    }

    public void setOnScheduleClickListener(OnScheduleClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnScheduleLongClickListener(OnScheduleLongClickListener listener) {
        this.longClickListener = listener;
    }

    private void updateNextIndex() {
        String now = getNow();
        nextIndex = -1;
        for (int i = 0; i < schedules.size(); i++) {
            if (schedules.get(i).departureTime.compareTo(now) >= 0) {
                nextIndex = i;
                break;
            }
        }
        if (nextIndex == -1 && !schedules.isEmpty()) {
            nextIndex = 0;
        }
    }

    private String getNow() {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_schedule, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Schedule schedule = schedules.get(position);
        holder.tvDeparture.setText(schedule.departureTime);
        holder.tvNextBadge.setVisibility(position == nextIndex ? View.VISIBLE : View.GONE);

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onScheduleClick(schedule);
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) longClickListener.onScheduleLongClick(schedule, v);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return schedules.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDeparture;
        TextView tvNextBadge;

        ViewHolder(View itemView) {
            super(itemView);
            tvDeparture = itemView.findViewById(R.id.tv_departure);
            tvNextBadge = itemView.findViewById(R.id.tv_next_badge);
        }
    }
}
