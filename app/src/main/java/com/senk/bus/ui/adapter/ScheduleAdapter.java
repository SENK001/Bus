package com.senk.bus.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.senk.bus.R;
import com.senk.bus.data.entity.Schedule;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 车次列表适配器。
 *
 * <p>列表顺序由 {@code ScheduleListFragment} 保证「下一班在最前」，NEXT 徽章只挂在 adapter 收到的
 * nextScheduleId 上（而不是固定 position 0），这样定时重排后徽章不会留在已过点的班次上。
 *
 * <p>使用 DiffUtil 做最小刷新：内容比较里包含「是否为下一班」，因此重排导致徽章易主时会重新绑定
 * 对应 item，而中间项移动不需要重新绑定。
 */
public class ScheduleAdapter extends RecyclerView.Adapter<ScheduleAdapter.ViewHolder> {

    private List<Schedule> schedules = Collections.emptyList();
    private long nextScheduleId = -1;
    private OnScheduleClickListener clickListener;

    public interface OnScheduleClickListener {
        void onScheduleClick(Schedule schedule);
    }

    /**
     * @param newSchedules     已按「距下一班由近到远」排好序的车次
     * @param newNextScheduleId 下一班车次的 id，没有有效班次时传 -1
     */
    public void setSchedules(List<Schedule> newSchedules, long newNextScheduleId) {
        final List<Schedule> incoming =
                newSchedules == null ? Collections.emptyList() : newSchedules;
        final List<Schedule> previous = schedules;
        final long previousNextId = nextScheduleId;
        final long incomingNextId = newNextScheduleId;
        if (previous == incoming && previousNextId == incomingNextId) {
            return;
        }
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return previous.size();
            }

            @Override
            public int getNewListSize() {
                return incoming.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPosition, int newPosition) {
                return previous.get(oldPosition).getId() == incoming.get(newPosition).getId();
            }

            @Override
            public boolean areContentsTheSame(int oldPosition, int newPosition) {
                Schedule oldItem = previous.get(oldPosition);
                Schedule newItem = incoming.get(newPosition);
                boolean badgeUnchanged = (oldItem.getId() == previousNextId)
                        == (newItem.getId() == incomingNextId);
                return badgeUnchanged
                        && Objects.equals(oldItem.getDepartureTime(), newItem.getDepartureTime());
            }
        });
        schedules = incoming;
        nextScheduleId = incomingNextId;
        diff.dispatchUpdatesTo(this);
    }

    public void setOnScheduleClickListener(OnScheduleClickListener listener) {
        this.clickListener = listener;
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
        holder.tvNextBadge.setVisibility(schedule.getId() == nextScheduleId
                ? View.VISIBLE : View.GONE);

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
