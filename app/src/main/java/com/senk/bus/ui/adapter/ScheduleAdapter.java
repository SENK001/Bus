package com.senk.bus.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.senk.bus.R;
import com.senk.bus.data.entity.Schedule;

import java.util.ArrayList;
import java.util.List;

public class ScheduleAdapter extends RecyclerView.Adapter<ScheduleAdapter.ViewHolder> {

    private List<Schedule> schedules = new ArrayList<>();
    private int nextIndex = -1;
    private OnScheduleClickListener clickListener;

    public interface OnScheduleClickListener {
        void onScheduleClick(Schedule schedule);
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

    private void updateNextIndex() {
        nextIndex = schedules.isEmpty() ? -1 : 0;
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
        holder.tvDeparture.setText(schedule.getDepartureTime());
        holder.tvNextBadge.setVisibility(position == nextIndex ? View.VISIBLE : View.GONE);

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onScheduleClick(schedule);
        });
    }

    @Override
    public int getItemCount() {
        return schedules.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDeparture;
        TextView tvNextBadge;

        ViewHolder(View itemView) {
            super(itemView);
            tvDeparture = itemView.findViewById(R.id.tv_departure);
            tvNextBadge = itemView.findViewById(R.id.tv_next_badge);
        }
    }
}
