package jackwtat.simplembta.adapters;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jackwtat.simplembta.model.Prediction;
import jackwtat.simplembta.model.Route;
import jackwtat.simplembta.model.Stop;
import jackwtat.simplembta.views.MapSearchPredictionItem;

public class MapSearchRecyclerViewAdapter
        extends RecyclerView.Adapter<MapSearchRecyclerViewAdapter.ViewHolder> {
    public static final String LOG_TAG = "MapSearchRecyclerViewAdapter";

    private ArrayList<DataHolder> dataHolders;

    private OnItemClickListener onItemClickListener;

    static class ViewHolder extends RecyclerView.ViewHolder {
        MapSearchPredictionItem predictionView;

        ViewHolder(MapSearchPredictionItem v) {
            super(v);
            predictionView = v;
        }
    }

    public class DataHolder implements Comparable<DataHolder> {
        Route route;
        Stop stop;
        ArrayList<Prediction> predictions;
        int direction;

        DataHolder(Route route, int direction) {
            this.route = route;
            this.direction = direction;
            this.stop = route.getNearestStop(direction);
            this.predictions = route.getPredictions(direction);
        }

        @Override
        public int compareTo(@NonNull DataHolder otherDataHolder) {
            if (this.stop == null && otherDataHolder.stop == null) {
                return this.route.compareTo(otherDataHolder.route);
            } else if (this.stop == null) {
                return 1;
            } else if (otherDataHolder.stop == null) {
                return -1;
            } else if (!this.stop.equals(otherDataHolder.stop)) {
                return this.stop.compareTo(otherDataHolder.stop);
            } else if (!this.route.equals(otherDataHolder.route)) {
                return this.route.compareTo(otherDataHolder.route);
            } else {
                return otherDataHolder.direction - this.direction;
            }
        }

        public Route getRoute() {
            return route;
        }

        public Stop getStop() {
            return stop;
        }

        public List<Prediction> getPredictions() {
            return predictions;
        }

        public int getDirection() {
            return direction;
        }
    }

    public MapSearchRecyclerViewAdapter() {
        dataHolders = new ArrayList<>();
    }

    @NonNull
    @Override
    public MapSearchRecyclerViewAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(new MapSearchPredictionItem(parent.getContext()));
    }

    @Override
    public void onBindViewHolder(@NonNull MapSearchRecyclerViewAdapter.ViewHolder holder, int position) {
        final int i = position;

        holder.predictionView.clear();
        holder.predictionView.setPredictions(
                dataHolders.get(i).route,
                dataHolders.get(i).stop,
                dataHolders.get(i).predictions,
                180 * 60000);

        holder.predictionView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (onItemClickListener != null) {
                    onItemClickListener.onItemClick(i);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return dataHolders.size();
    }

    public DataHolder getData(int position) {
        return dataHolders.get(position);
    }

    public void setRoutes(List<Route> routes) {
        dataHolders.clear();

        for (Route route : routes) {
            if (route.hasPickUps(Route.INBOUND) || route.hasPickUps(Route.OUTBOUND)) {
                if (route.hasPickUps(Route.INBOUND)) {
                    dataHolders.add(new DataHolder(route, Route.INBOUND));
                }
                if (route.hasPickUps(Route.OUTBOUND)) {
                    dataHolders.add(new DataHolder(route, Route.OUTBOUND));
                }
            } else if (route.hasNearbyStops()) {
                Stop inboundStop = route.getNearestStop(Route.INBOUND);
                Stop outboundStop = route.getNearestStop(Route.OUTBOUND);

                if (inboundStop != null && inboundStop.equals(outboundStop)) {
                    dataHolders.add(new DataHolder(route, Route.INBOUND));
                } else {
                    if (route.getNearestStop(Route.INBOUND) != null) {
                        dataHolders.add(new DataHolder(route, Route.INBOUND));
                    }
                    if (route.getNearestStop(Route.OUTBOUND) != null) {
                        dataHolders.add(new DataHolder(route, Route.OUTBOUND));
                    }
                }
            } else {
                dataHolders.add(new DataHolder(route, Route.NULL_DIRECTION));
            }
        }

        Collections.sort(dataHolders);

        notifyDataSetChanged();
    }

    public void clear() {
        dataHolders.clear();
        notifyDataSetChanged();
    }

    public void setOnItemClickListener(MapSearchRecyclerViewAdapter.OnItemClickListener onItemClickListener) {
        this.onItemClickListener = onItemClickListener;
    }

    public interface OnItemClickListener {
        void onItemClick(int i);
    }
}
