package jackwtat.simplembta.adapters.spinners;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import jackwtat.simplembta.R;
import jackwtat.simplembta.model.Direction;

public class DirectionsSpinnerAdapter extends ArrayAdapter<Direction> {
    private Context context;
    private Direction[] directions;
    private Direction selectedDirection;

    public DirectionsSpinnerAdapter(Context context, Direction[] directions) {
        super(context, 0, directions);
        this.context = context;
        this.directions = directions;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        return createItemView(directions[position], parent, R.layout.spinner_item_direction_selected);
    }

    @Override
    public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View listItem;
        Direction direction = directions[position];

        if (direction.equals(selectedDirection)) {
            listItem = createItemView(direction, parent, R.layout.spinner_item_direction_dropdown_selected);
        } else {
            listItem = createItemView(direction, parent, R.layout.spinner_item_direction_dropdown);
        }

        return listItem;
    }

    private View createItemView(Direction direction, @NonNull ViewGroup parent, @NonNull int layout) {
        View listItem = LayoutInflater.from(context).inflate(layout, parent, false);

        TextView nameTextView = listItem.findViewById(R.id.direction_name_text_view);
        nameTextView.setText(direction.getName());

        return listItem;
    }

    @Nullable
    @Override
    public Direction getItem(int position) {
        return directions[position];
    }

    public void setSelectedDirection(Direction direction) {
        selectedDirection = direction;
    }
}
