package jackwtat.simplembta.views;

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.Spinner;

import jackwtat.simplembta.R;
import jackwtat.simplembta.adapters.DirectionsSpinnerAdapter;
import jackwtat.simplembta.adapters.StopsSpinnerAdapter;
import jackwtat.simplembta.model.Direction;
import jackwtat.simplembta.model.Stop;

public class StopSelectorView extends LinearLayout implements AdapterView.OnItemSelectedListener {
    private View rootView;
    private Spinner directionSpinner;
    private Spinner stopSpinner;

    private Direction[] directions = {};
    private Stop[] stops = {};

    private OnDirectionSelectedListener onDirectionSelectedListener;
    private OnStopSelectedListener onStopSelectedListener;

    public StopSelectorView(Context context) {
        super(context);
        init(context);
    }

    public StopSelectorView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public StopSelectorView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        rootView = inflate(context, R.layout.stop_selector_view, this);
        directionSpinner = rootView.findViewById(R.id.direction_spinner);
        directionSpinner.setOnItemSelectedListener(this);
        stopSpinner = rootView.findViewById(R.id.stop_spinner);
        stopSpinner.setOnItemSelectedListener(this);
    }

    public void populateDirectionSpinner(Direction[] directions) {
        this.directions = directions;
        DirectionsSpinnerAdapter adapter = new DirectionsSpinnerAdapter(getContext(), directions);
        directionSpinner.setAdapter(adapter);
    }

    public void populateStopSpinner(Stop[] stops) {
        this.stops = stops;
        StopsSpinnerAdapter adapter = new StopsSpinnerAdapter(getContext(), stops);
        stopSpinner.setAdapter(adapter);
    }

    public void selectDirection(int directionId) {
        if (directionId >= 0 && directionId < directionSpinner.getCount()) {
            directionSpinner.setSelection(directionId);
        }
    }

    public void selectStop(String stopId) {
        for (int i = 0; i < stops.length; i++) {
            if (stops[i].getId().equals(stopId)) {
                stopSpinner.setSelection(i);
                break;
            }
        }
    }

    public Direction getDirection(int position) {
        return directions[position];
    }

    public Stop getStop(int position) {
        return stops[position];
    }

    public void setOnDirectionSelectedListener(OnDirectionSelectedListener onDirectionSelectedListener) {
        this.onDirectionSelectedListener = onDirectionSelectedListener;
    }

    public void setOnStopSelectedListener(OnStopSelectedListener onStopSelectedListener) {
        this.onStopSelectedListener = onStopSelectedListener;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        switch (parent.getId()) {
            case R.id.direction_spinner:
                Direction selectedDirection = (Direction) parent.getItemAtPosition(position);
                onDirectionSelectedListener.onDirectionSelected(selectedDirection);
                break;
            case R.id.stop_spinner:
                Stop selectedStop = (Stop) parent.getItemAtPosition(position);
                onStopSelectedListener.onStopSelected(selectedStop);
                break;
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }

    public interface OnDirectionSelectedListener {
        void onDirectionSelected(Direction direction);
    }

    public interface OnStopSelectedListener {
        void onStopSelected(Stop stop);
    }
}
