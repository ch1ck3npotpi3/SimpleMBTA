package jackwtat.simplembta.views;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import jackwtat.simplembta.R;
import jackwtat.simplembta.mbta.structure.Mode;
import jackwtat.simplembta.mbta.structure.Route;

public class RouteNameView extends RelativeLayout {

    final static public int SQUARE_BACKGROUND = 0;
    final static public int ROUNDED_BACKGROUND = 1;
    final static public int SMALL_TEXT_SIZE = 22;
    final static public int LARGE_TEXT_SIZE = 28;

    Route route;
    int textSize;
    int backgroundShape;
    boolean nameAbbreviated;
    boolean colorAccentEnabled;

    View rootView;
    View routeNameAccentView;
    TextView routeNameTextView;

    public RouteNameView(Context context) {
        super(context);
        initializeViews(context, SQUARE_BACKGROUND);
    }

    public RouteNameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initializeViews(context, SQUARE_BACKGROUND);
    }

    public RouteNameView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initializeViews(context, SQUARE_BACKGROUND);
    }

    public RouteNameView(Context context, Route route, int textSize, int backgroundShape,
                         boolean abbreviateName, boolean enableColorAccent) {
        super(context);
        initializeViews(context, backgroundShape);

        this.route = route;
        this.textSize = textSize;
        this.backgroundShape = backgroundShape;
        nameAbbreviated = abbreviateName;
        colorAccentEnabled = enableColorAccent;

        setBackground();
        setColorAccent();
        setRouteName();
    }

    private void setBackground() {
        Drawable background;

        // Background shape
        if (backgroundShape == ROUNDED_BACKGROUND) {
            background = getContext().getResources().getDrawable(R.drawable.rounded_background);
        } else {
            background = getContext().getResources().getDrawable(R.drawable.square_background);
        }

        // Background color
        DrawableCompat.setTint(background, Color.parseColor(route.getPrimaryColor()));

        // Set background
        setBackground(background);
    }

    private void setColorAccent() {
        if (colorAccentEnabled && route.getMode() == Mode.BUS &&
                !route.getLongName().contains("Silver Line")) {
            routeNameAccentView.setVisibility(View.VISIBLE);
        } else {
            routeNameAccentView.setVisibility(View.GONE);
        }
    }

    private void setRouteName() {
        routeNameTextView.setTextColor(Color.parseColor(route.getTextColor()));
        routeNameTextView.setTextSize(textSize);

        if (nameAbbreviated) {
            routeNameTextView.setText(route.getShortDisplayName(getContext()));
        } else {
            routeNameTextView.setText(route.getLongDisplayName(getContext()));
        }
    }

    private void initializeViews(Context context, int backgroundShape) {
        rootView = inflate(context, R.layout.route_name_view, this);
        routeNameAccentView = rootView.findViewById(R.id.route_name_accent);
        routeNameTextView = rootView.findViewById(R.id.route_name_text_view);
    }
}
