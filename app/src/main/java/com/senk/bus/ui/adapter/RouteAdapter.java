package com.senk.bus.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.senk.bus.R;
import com.senk.bus.data.entity.Route;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RouteAdapter extends RecyclerView.Adapter<RouteAdapter.ViewHolder> {

    private List<Route> routes = new ArrayList<>();
    private final Map<Long, String> nextTimes = new HashMap<>();
    private OnRouteClickListener clickListener;
    private OnRouteLongClickListener longClickListener;

    public interface OnRouteClickListener {
        void onRouteClick(Route route);
    }

    public interface OnRouteLongClickListener {
        void onRouteLongClick(Route route, View anchor);
    }

    public void setRoutes(List<Route> routes) {
        this.routes = routes;
        notifyDataSetChanged();
    }

    public void setNextTime(long routeId, String time) {
        nextTimes.put(routeId, time);
    }

    public void clearNextTimes() {
        nextTimes.clear();
    }

    public void setOnRouteClickListener(OnRouteClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnRouteLongClickListener(OnRouteLongClickListener listener) {
        this.longClickListener = listener;
    }

    public Route getRouteAt(int position) {
        if (position >= 0 && position < routes.size()) {
            return routes.get(position);
        }
        return null;
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
        holder.tvName.setText(route.name);
        holder.tvDesc.setText(route.origin + " → " + route.destination);

        String nextTime = nextTimes.get(route.id);
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
