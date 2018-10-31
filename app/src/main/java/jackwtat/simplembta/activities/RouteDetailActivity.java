package jackwtat.simplembta.activities;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CoordinatorLayout;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.JointType;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap;
import com.google.maps.android.PolyUtil;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import jackwtat.simplembta.R;
import jackwtat.simplembta.adapters.RouteDetailRecyclerViewAdapter;
import jackwtat.simplembta.asyncTasks.RouteDetailPredictionsAsyncTask;
import jackwtat.simplembta.asyncTasks.ServiceAlertsAsyncTask;
import jackwtat.simplembta.asyncTasks.ShapesAsyncTask;
import jackwtat.simplembta.asyncTasks.VehiclesAsyncTask;
import jackwtat.simplembta.clients.NetworkConnectivityClient;
import jackwtat.simplembta.model.Prediction;
import jackwtat.simplembta.model.Route;
import jackwtat.simplembta.model.Routes;
import jackwtat.simplembta.model.ServiceAlert;
import jackwtat.simplembta.model.Shape;
import jackwtat.simplembta.model.Stop;
import jackwtat.simplembta.model.Vehicle;
import jackwtat.simplembta.utilities.ErrorManager;
import jackwtat.simplembta.views.ServiceAlertsIndicatorView;
import jackwtat.simplembta.views.ServiceAlertsListView;
import jackwtat.simplembta.views.ServiceAlertsTitleView;

public class RouteDetailActivity extends AppCompatActivity implements OnMapReadyCallback,
        ErrorManager.OnErrorChangedListener, RouteDetailPredictionsAsyncTask.OnPostExecuteListener,
        ShapesAsyncTask.OnPostExecuteListener, ServiceAlertsAsyncTask.OnPostExecuteListener,
        VehiclesAsyncTask.OnPostExecuteListener {
    public static final String LOG_TAG = "RouteDetailActivity";

    // Predictions auto update rate
    public static final long PREDICTIONS_UPDATE_RATE = 15000;

    // Service alerts auto update rate
    public static final long SERVICE_ALERTS_UPDATE_RATE = 60000;

    // Vehicle locations auto update rate
    public static final long VEHICLES_UPDATE_RATE = 15000;

    // Shapes auto update rate
    public static final long SHAPES_UPDATE_RATE = 15000;

    // Maximum age of prediction
    public static final long MAXIMUM_PREDICTION_AGE = 90000;

    private AppBarLayout appBarLayout;
    private MapView mapView;
    private GoogleMap gMap;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private TextView noPredictionsTextView;
    private ProgressBar mapProgressBar;
    private TextView errorTextView;
    private ServiceAlertsIndicatorView serviceAlertsIndicatorView;

    private String realTimeApiKey;
    private RouteDetailPredictionsAsyncTask predictionsAsyncTask;
    private ServiceAlertsAsyncTask serviceAlertsAsyncTask;
    private ShapesAsyncTask shapesAsyncTask;
    private VehiclesAsyncTask vehiclesAsyncTask;
    private NetworkConnectivityClient networkConnectivityClient;
    private ErrorManager errorManager;
    private RouteDetailRecyclerViewAdapter recyclerViewAdapter;
    private Timer timer;

    private boolean refreshing = false;
    private boolean mapReady = false;
    private boolean shapesLoaded = false;
    private boolean mapCameraIsMoving = false;
    private boolean userIsScrolling = false;
    private long refreshTime = 0;

    private Route route;
    private int direction;
    private Shape[] shapes = new Shape[0];
    private ArrayList<Polyline> polylines = new ArrayList<>();
    private ArrayList<Marker> stopMarkers = new ArrayList<>();
    private Vehicle[] vehicles = new Vehicle[0];
    private ArrayList<Marker> vehicleMarkers = new ArrayList<>();
    private Marker selectedStopMarker;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_route_detail);

        // Get MBTA realTime API key
        realTimeApiKey = getResources().getString(R.string.v3_mbta_realtime_api_key);

        // Get values passed from calling activity/fragment
        Intent intent = getIntent();
        route = (Route) intent.getSerializableExtra("route");
        direction = intent.getIntExtra("direction", Route.NULL_DIRECTION);
        refreshTime = intent.getLongExtra("refreshTime", MAXIMUM_PREDICTION_AGE + 1);

        // Get error textview
        errorTextView = findViewById(R.id.error_message_text_view);

        // Get network connectivity client
        networkConnectivityClient = new NetworkConnectivityClient(this);

        // Set action bar
        setTitle(route.getLongDisplayName(this) + " - " + route.getDirectionName(direction));
        if (Build.VERSION.SDK_INT >= 21) {
            // Create color for status bar
            float[] hsv = new float[3];
            Color.colorToHSV(Color.parseColor(route.getPrimaryColor()), hsv);
            hsv[2] *= .8f;

            // Set status bar color
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.setStatusBarColor(Color.HSVToColor(hsv));

            // Set action bar background color
            ActionBar actionBar = getSupportActionBar();
            actionBar.setBackgroundDrawable(
                    new ColorDrawable(Color.parseColor(route.getPrimaryColor())));
        }

        // Get app bar and app bar params
        appBarLayout = findViewById(R.id.app_bar_layout);
        CoordinatorLayout.LayoutParams params =
                (CoordinatorLayout.LayoutParams) appBarLayout.getLayoutParams();
        params.height = (int) (getResources().getDisplayMetrics().heightPixels * .6);

        // Disable scrolling inside app bar
        AppBarLayout.Behavior behavior = new AppBarLayout.Behavior();
        behavior.setDragCallback(new AppBarLayout.Behavior.DragCallback() {
            @Override
            public boolean canDrag(AppBarLayout appBarLayout) {
                return false;
            }
        });
        params.setBehavior(behavior);

        // Get the no predictions indicator
        noPredictionsTextView = findViewById(R.id.no_predictions_text_view);

        // Get and initialize map view
        mapView = findViewById(R.id.map_view);
        mapView.getLayoutParams().height =
                (int) (getResources().getDisplayMetrics().heightPixels * .6);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        // Get map progress bar
        mapProgressBar = findViewById(R.id.map_progress_bar);

        // Get service alerts indicator
        serviceAlertsIndicatorView = findViewById(R.id.service_alerts_indicator_view);

        // Get and initialize swipe refresh layout
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        swipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(this,
                R.color.colorAccent));
        swipeRefreshLayout.setEnabled(false);

        // Get recycler view
        recyclerView = findViewById(R.id.predictions_recycler_view);

        // Set recycler view layout
        recyclerView.setLayoutManager(new GridLayoutManager(this, 1));

        // Disable recycler view scrolling until predictions loaded;
        recyclerView.setNestedScrollingEnabled(false);

        // Add on scroll listener
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    userIsScrolling = false;
                    refreshPredictions();
                    refreshServiceAlertsView();
                    if (!shapesLoaded) {
                        refreshShapes();
                    }

                } else if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    userIsScrolling = true;
                }
            }
        });

        // Create and set the recycler view adapter
        recyclerViewAdapter = new RouteDetailRecyclerViewAdapter();
        recyclerView.setAdapter(recyclerViewAdapter);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        gMap = googleMap;
        mapReady = true;

        // Move the map camera to the last known location
        Stop stop = route.getNearestStop(direction);
        LatLng latLng = (stop == null)
                ? new LatLng(42.3604, -71.0580)
                : new LatLng(stop.getLocation().getLatitude(), stop.getLocation().getLongitude());
        gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15));

        // Set the action listeners
        gMap.setOnCameraMoveStartedListener(new GoogleMap.OnCameraMoveStartedListener() {
            @Override
            public void onCameraMoveStarted(int reason) {
                mapCameraIsMoving = true;
            }
        });
        gMap.setOnCameraIdleListener(new GoogleMap.OnCameraIdleListener() {
            @Override
            public void onCameraIdle() {
                if (mapCameraIsMoving) {
                    mapCameraIsMoving = false;
                }
            }
        });
        gMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                if (marker.getTag() instanceof Stop) {
                    selectedStopMarker = marker;
                    selectedStopMarker.showInfoWindow();

                    route.setNearestStop(direction, (Stop) marker.getTag(), true);

                    swipeRefreshLayout.setRefreshing(true);
                    clearPredictions();
                    forceUpdate();
                } else if (selectedStopMarker != null) {
                    selectedStopMarker.showInfoWindow();
                }

                return true;
            }
        });
        gMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                selectedStopMarker.showInfoWindow();
            }
        });

        // Set the map style
        gMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style));

        // Set the map UI settings
        UiSettings mapUiSettings = gMap.getUiSettings();
        mapUiSettings.setRotateGesturesEnabled(false);
        mapUiSettings.setTiltGesturesEnabled(false);
        mapUiSettings.setZoomControlsEnabled(true);

        // Load the route outline and stop markers
        getShapes();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mapView.onStart();

        errorManager = ErrorManager.getErrorManager();
        errorManager.registerOnErrorChangeListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();

        // Refresh the activity to update UI so that the predictions and service alerts are accurate
        // as of the last update
        refreshPredictions();
        refreshServiceAlertsView();
        refreshVehicles();

        // Get the route shapes if there aren't any
        if (shapes.length == 0) {
            getShapes();
        }

        // If too much time has elapsed since last refresh, then clear predictions and force update
        if (new Date().getTime() - refreshTime > MAXIMUM_PREDICTION_AGE) {
            clearPredictions();
            swipeRefreshLayout.setRefreshing(true);
            forceUpdate();

            // If there are no predictions displayed in the recycler view, then force a refresh
        } else if (recyclerViewAdapter.getItemCount() < 1) {
            swipeRefreshLayout.setRefreshing(true);
            forceUpdate();

            // Otherwise, background update
        } else {
            backgroundUpdate();
        }

        timer = new Timer();
        timer.schedule(new PredictionsUpdateTimerTask(), 0, PREDICTIONS_UPDATE_RATE);
        timer.schedule(new ServiceAlertsUpdateTimerTask(), 0, SERVICE_ALERTS_UPDATE_RATE);
        timer.schedule(new VehiclesUpdateTimerTask(), 0, VEHICLES_UPDATE_RATE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();

        if (predictionsAsyncTask != null) {
            predictionsAsyncTask.cancel(true);
        }

        if (shapesAsyncTask != null) {
            shapesAsyncTask.cancel(true);
        }

        if (vehiclesAsyncTask != null) {
            vehiclesAsyncTask.cancel(true);
        }

        timer.cancel();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    public void onErrorChanged() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                errorTextView.setOnClickListener(null);

                if (errorManager.hasNetworkError()) {
                    errorTextView.setText(R.string.network_error_text);
                    errorTextView.setVisibility(View.VISIBLE);

                    route.clearPredictions(Route.INBOUND);
                    route.clearPredictions(Route.OUTBOUND);
                    route.clearServiceAlerts();

                    vehicles = new Vehicle[0];

                    refreshPredictions();
                    refreshServiceAlertsView();
                    refreshVehicles();

                } else if (!errorManager.hasNetworkError()) {
                    errorTextView.setVisibility(View.GONE);
                }
            }
        });
    }

    private void backgroundUpdate() {
        if (!refreshing) {
            getPredictions();
        }
    }

    private void forceUpdate() {
        getPredictions();
    }

    private void getPredictions() {
        if (networkConnectivityClient.isConnected()) {
            errorManager.setNetworkError(false);

            refreshing = true;

            if (predictionsAsyncTask != null) {
                predictionsAsyncTask.cancel(true);
            }

            if (route != null && route.getNearestStop(direction) != null) {
                predictionsAsyncTask = new RouteDetailPredictionsAsyncTask(realTimeApiKey, route,
                        direction, this);
                predictionsAsyncTask.execute();
            } else {
                refreshPredictions();
            }
        } else {
            errorManager.setNetworkError(true);
            refreshing = false;
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    @Override
    public void onPostExecute(List<Prediction> predictions) {
        refreshing = false;
        refreshTime = new Date().getTime();

        route.clearPredictions(direction);
        route.addAllPredictions(predictions);

        refreshPredictions();
    }

    private void refreshPredictions() {
        if (!userIsScrolling) {
            recyclerViewAdapter.setPredictions(route.getPredictions(direction));
            swipeRefreshLayout.setRefreshing(false);

            if (recyclerViewAdapter.getItemCount() == 0) {
                if (route.getNearestStop(direction) == null) {
                    noPredictionsTextView.setText(getResources().getString(R.string.select_stop));
                } else {
                    noPredictionsTextView.setText(getResources().getString(R.string.no_predictions_this_stop));
                }

                noPredictionsTextView.setVisibility(View.VISIBLE);
                appBarLayout.setExpanded(true);
                recyclerView.setNestedScrollingEnabled(false);
            } else {
                noPredictionsTextView.setVisibility(View.GONE);
                recyclerView.setNestedScrollingEnabled(true);
            }
        }
    }

    private void clearPredictions() {
        recyclerViewAdapter.clear();
        noPredictionsTextView.setVisibility(View.GONE);
        appBarLayout.setExpanded(true);
        recyclerView.setNestedScrollingEnabled(false);
    }

    private void getServiceAlerts() {
        if (networkConnectivityClient.isConnected()) {
            errorManager.setNetworkError(false);

            if (serviceAlertsAsyncTask != null) {
                serviceAlertsAsyncTask.cancel(true);
            }

            serviceAlertsAsyncTask = new ServiceAlertsAsyncTask(
                    realTimeApiKey,
                    route.getId(),
                    this);
            serviceAlertsAsyncTask.execute();
        } else {
            errorManager.setNetworkError(true);
        }
    }

    @Override
    public void onPostExecute(ServiceAlert[] serviceAlerts) {
        route.clearServiceAlerts();
        route.addAllServiceAlerts(serviceAlerts);

        refreshServiceAlertsView();
    }

    private void refreshServiceAlertsView() {
        if (!userIsScrolling) {
            if (route.getServiceAlerts().size() > 0) {
                serviceAlertsIndicatorView.setServiceAlerts(route);
                serviceAlertsIndicatorView.setVisibility(View.VISIBLE);
            } else {
                serviceAlertsIndicatorView.setVisibility(View.GONE);
            }
        }
    }

    private void getShapes() {
        if (networkConnectivityClient.isConnected()) {
            errorManager.setNetworkError(false);

            if (shapesAsyncTask != null) {
                shapesAsyncTask.cancel(true);
            }

            shapesAsyncTask = new ShapesAsyncTask(
                    realTimeApiKey,
                    route.getId(),
                    direction,
                    this);
            shapesAsyncTask.execute();
        } else {
            errorManager.setNetworkError(true);
        }
    }

    @Override
    public void onPostExecute(Shape[] shapes) {
        this.shapes = shapes;

        refreshShapes();
    }

    private void refreshShapes() {
        if (!userIsScrolling) {
            for (Polyline pl : polylines) {
                pl.remove();
            }
            polylines.clear();

            for (Marker m : stopMarkers) {
                m.remove();
            }
            stopMarkers.clear();

            HashMap<String, Stop> distinctStops = new HashMap<>();

            for (Shape shape : shapes) {
                if (shape.getPriority() > -1 && shape.getStops().length > 1) {
                    drawPolyline(shape);

                    for (Stop stop : shape.getStops()) {
                        distinctStops.put(stop.getId(), stop);
                    }
                }
            }

            Stop currentStop = route.getNearestStop(direction);
            selectedStopMarker = null;
            LatLngBounds.Builder boundsBuilder = LatLngBounds.builder();

            for (Stop stop : distinctStops.values()) {
                Marker currentMarker = drawStopMarker(stop);
                stopMarkers.add(currentMarker);
                boundsBuilder.include(currentMarker.getPosition());

                if (currentStop != null && (currentStop.equals(stop) ||
                        currentStop.isParentOf(stop.getId()) || stop.isParentOf(currentStop.getId()))) {
                    selectedStopMarker = currentMarker;
                }
            }

            if (selectedStopMarker != null) {
                selectedStopMarker.showInfoWindow();
                gMap.moveCamera(
                        CameraUpdateFactory.newLatLngZoom(selectedStopMarker.getPosition(), 15));
            } else if (currentStop != null) {
                selectedStopMarker = drawStopMarker(currentStop);
                selectedStopMarker.showInfoWindow();
                boundsBuilder.include(selectedStopMarker.getPosition());
                gMap.moveCamera(
                        CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 50));
            } else if (distinctStops.size() > 0) {
                gMap.moveCamera(
                        CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 50));
            }

            mapProgressBar.setVisibility(View.GONE);
            shapesLoaded = true;
        }
    }

    private void drawPolyline(@NonNull Shape shape) {
        List<LatLng> shapeCoordinates = PolyUtil.decode(shape.getPolyline());

        polylines.add(gMap.addPolyline(new PolylineOptions()
                .addAll(shapeCoordinates)
                .color(Color.parseColor("#FFFFFF"))
                .zIndex(0)
                .jointType(JointType.ROUND)
                .startCap(new RoundCap())
                .endCap(new RoundCap())
                .width(14)));

        polylines.add(gMap.addPolyline(new PolylineOptions()
                .addAll(shapeCoordinates)
                .color(Color.parseColor(route.getPrimaryColor()))
                .zIndex(1)
                .jointType(JointType.ROUND)
                .startCap(new RoundCap())
                .endCap(new RoundCap())
                .width(8)));
    }

    private Marker drawStopMarker(@NonNull Stop stop) {
        Marker stopMarker = gMap.addMarker(new MarkerOptions()
                .position(new LatLng(
                        stop.getLocation().getLatitude(), stop.getLocation().getLongitude()))
                .anchor(0.5f, 0.5f)
                .title(stop.getName())
                .zIndex(2)
                .flat(true)
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.mbta_stop_icon)));

        stopMarker.setTag(stop);

        return stopMarker;
    }

    private void getVehicles() {
        if (networkConnectivityClient.isConnected()) {
            errorManager.setNetworkError(false);

            if (vehiclesAsyncTask != null) {
                vehiclesAsyncTask.cancel(true);
            }

            vehiclesAsyncTask = new VehiclesAsyncTask(
                    realTimeApiKey,
                    route.getId(),
                    direction,
                    this);
            vehiclesAsyncTask.execute();
        } else {
            errorManager.setNetworkError(true);
        }
    }

    @Override
    public void onPostExecute(Vehicle[] vehicles) {
        this.vehicles = vehicles;

        refreshVehicles();
    }

    private void refreshVehicles() {
        if (!userIsScrolling) {
            for (Marker m : vehicleMarkers) {
                m.remove();
            }
            vehicleMarkers.clear();

            for (Vehicle vehicle : vehicles) {
                drawVehicleMarker(vehicle);
            }
        }
    }

    private void drawVehicleMarker(@NonNull Vehicle vehicle) {
        Marker vehicleMarker = gMap.addMarker(new MarkerOptions()
                .position(new LatLng(
                        vehicle.getLocation().getLatitude(), vehicle.getLocation().getLongitude()))
                .title(vehicle.getLabel())
                .zIndex(3)
                .flat(true)
        );

        vehicleMarker.setTag(vehicle);

        vehicleMarkers.add(vehicleMarker);
    }

    private class PredictionsUpdateTimerTask extends TimerTask {
        @Override
        public void run() {
            backgroundUpdate();
        }
    }

    private class ServiceAlertsUpdateTimerTask extends TimerTask {
        @Override
        public void run() {
            getServiceAlerts();
        }
    }

    private class VehiclesUpdateTimerTask extends TimerTask {
        @Override
        public void run() {
            getVehicles();
        }
    }
}
