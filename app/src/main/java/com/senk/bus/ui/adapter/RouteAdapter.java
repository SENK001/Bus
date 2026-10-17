package com.senk.bus.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.senk.bus.R;
import com.senk.bus.data.entity.Route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class RouteAdapter extends RecyclerView.Adapter<RouteAdapter.ViewHolder> {

    private List<Route> routes = Collections.emptyList();
    private final Map<Long, String> nextTimes = new HashMap<>();
    private OnRouteClickListener clickListener;
    private OnRouteLongClickListener longClickListener;

    public interface OnRouteClickListener {
        void onRouteClick(Route route);
    }

    public interface OnRouteLongClickListener {
        void onRouteLongClick(Route route, View anchor);
    }

    /**
     * 替换路线列表，用 DiffUtil 计算最小更新，避免整表刷新导致滚动位置/闪烁。
     * 卡片右侧的下一班时间来自 {@link #nextTimes}，与列表顺序无关。
     */
    public void setRoutes(List<Route> newRoutes) {
        final List<Route> incoming = newRoutes == null ? Collections.emptyList() : newRoutes;
        final List<Route> previous = routes;
        if (previous == incoming) {
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
                Route oldItem = previous.get(oldPosition);
                Route newItem = incoming.get(newPosition);
                return Objects.equals(oldItem.getName(), newItem.getName())
                        && Objects.equals(oldItem.getOrigin(), newItem.getOrigin())
                        && Objects.equals(oldItem.getDestination(), newItem.getDestination())
                        && oldItem.isFavorite() == newItem.isFavorite()
                        && oldItem.isDefault() == newItem.isDefault();
            }
        });
        routes = incoming;
        diff.dispatchUpdatesTo(this);
    }

    /** 用一次计算结果替换所有路线的下一班时间；内容没变时不触发刷新。 */
    public void setNextTimes(Map<Long, String> times) {
        Map<Long, String> incoming = times == null ? Collections.emptyMap() : times;
        if (nextTimes.equals(incoming)) {
            return;
        }
        nextTimes.clear();
        nextTimes.putAll(incoming);
        if (!routes.isEmpty()) {
            notifyItemRangeChanged(0, routes.size());
        }
    }

    public void setOnRouteClickListener(OnRouteClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnRouteLongClickListener(OnRouteLongClickListener listener) {
        this.longClickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_route, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Route route = routes.get(position);
        holder.tvName.setText(route.getName());
        holder.tvDesc.setText(holder.itemView.getContext().getString(
                R.string.route_desc_format, route.getOrigin(), route.getDestination()));

        String nextTime = nextTimes.get(route.getId());
        if (nextTime != null) {
            holder.tvNextTime.setText(nextTime);
            holder.tvNextTime.setVisibility(View.VISIBLE);
        } else {
            holder.tvNextTime.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onRouteClick(route);
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) longClickListener.onRouteLongClick(route, v);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return routes.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvDesc;
        TextView tvNextTime;

        ViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_route_name);
            tvDesc = itemView.findViewById(R.id.tv_route_desc);
            tvNextTime = itemView.findViewById(R.id.tv_next_time);
        }
    }
}
