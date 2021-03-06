package jackwtat.simplembta.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.JointType;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap;
import com.google.android.material.appbar.AppBarLayout;
import com.google.maps.android.PolyUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import jackwtat.simplembta.R;
import jackwtat.simplembta.adapters.RouteSearchRecyclerViewAdapter;
import jackwtat.simplembta.asyncTasks.PredictionsRouteSearchAsyncTask;
import jackwtat.simplembta.asyncTasks.ServiceAlertsAsyncTask;
import jackwtat.simplembta.asyncTasks.ShapesAsyncTask;
import jackwtat.simplembta.asyncTasks.VehiclesByRouteAsyncTask;
import jackwtat.simplembta.clients.NetworkConnectivityClient;
import jackwtat.simplembta.clients.RealTimeApiClient;
import jackwtat.simplembta.jsonParsers.ShapesJsonParser;
import jackwtat.simplembta.model.Direction;
import jackwtat.simplembta.model.Prediction;
import jackwtat.simplembta.model.ServiceAlert;
import jackwtat.simplembta.model.routes.BlueLine;
import jackwtat.simplembta.model.routes.GreenLine;
import jackwtat.simplembta.model.routes.GreenLineCombined;
import jackwtat.simplembta.model.routes.OrangeLine;
import jackwtat.simplembta.model.routes.RedLine;
import jackwtat.simplembta.model.Route;
import jackwtat.simplembta.model.Shape;
import jackwtat.simplembta.model.Stop;
import jackwtat.simplembta.model.Vehicle;
import jackwtat.simplembta.model.routes.SilverLine;
import jackwtat.simplembta.utilities.Constants;
import jackwtat.simplembta.utilities.DisplayNameUtil;
import jackwtat.simplembta.utilities.ErrorManager;
import jackwtat.simplembta.utilities.PastDataHolder;
import jackwtat.simplembta.utilities.RawResourceReader;
import jackwtat.simplembta.views.NoPredictionsView;
import jackwtat.simplembta.views.RouteDetailSpinners;
import jackwtat.simplembta.views.ServiceAlertsIndicatorView;

public class RouteDetailActivity extends AppCompatActivity implements OnMapReadyCallback,
        ErrorManager.OnErrorChangedListener, RouteDetailSpinners.OnDirectionSelectedListener,
        RouteDetailSpinners.OnStopSelectedListener, Constants {
    public static final String LOG_TAG = "RouteDetailActivity";

    private AppBarLayout appBarLayout;
    private MapView mapView;
    private GoogleMap gMap;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ServiceAlertsIndicatorView serviceAlertsIndicatorView;
    private RecyclerView recyclerView;
    private NoPredictionsView noPredictionsView;
    private ProgressBar mapProgressBar;
    private TextView errorTextView;
    private RouteDetailSpinners routeDetailSpinners;

    private String realTimeApiKey;
    private NetworkConnectivityClient networkConnectivityClient;
    private ErrorManager errorManager;
    private RouteSearchRecyclerViewAdapter recyclerViewAdapter;
    private Timer timer;

    private PredictionsRouteSearchAsyncTask predictionsAsyncTask;
    private ShapesAsyncTask shapesAsyncTask;
    private VehiclesByRouteAsyncTask vehiclesAsyncTask;
    private ServiceAlertsAsyncTask serviceAlertsAsyncTask;

    private boolean dataRefreshing = false;
    private boolean loaded = false;
    private boolean mapReady = false;
    private boolean shapesLoaded = false;
    private boolean mapCameraIsMoving = false;
    private boolean userIsScrolling = false;
    private long refreshTime = 0;

    private Location userLocation = new Location("userLocation");
    private Route selectedRoute;
    private int selectedDirectionId;
    private ArrayList<Polyline> polylines = new ArrayList<>();
    private HashMap<String, Marker> stopMarkers = new HashMap<>();
    private HashMap<String, Marker> vehicleMarkers = new HashMap<>();
    private Marker selectedStopMarker;
    private Marker selectedVehicleMarker;
    private PastDataHolder pastData = PastDataHolder.getHolder();
    private HashMap<String, Vehicle> vehicles = new HashMap<>();
    private HashMap<String, Vehicle> vehicleTrips = new HashMap<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_route_detail);

        // Get MBTA realTime API key
        realTimeApiKey = getResources().getString(R.string.v3_mbta_realtime_api_key_general);

        // Get data saved from previous session
        if (savedInstanceState != null) {
            selectedRoute = (Route) savedInstanceState.getSerializable("route");
            selectedDirectionId = savedInstanceState.getInt("direction");
            refreshTime = savedInstanceState.getLong("refreshTime");
            userLocation.setLatitude(0);
            userLocation.setLongitude(0);

            if (new Date().getTime() - refreshTime > MAXIMUM_PREDICTION_AGE) {
                for (Direction d : selectedRoute.getAllDirections()) {
                    selectedRoute.clearPredictions(d.getId());
                }
            }

            // Get values passed from calling activity/fragment
        } else {
            Intent intent = getIntent();
            selectedRoute = (Route) intent.getSerializableExtra("route");
            selectedDirectionId = intent.getIntExtra("direction", Direction.NULL_DIRECTION);
            refreshTime = intent.getLongExtra("refreshTime", MAXIMUM_PREDICTION_AGE + 1);

            userLocation.setLatitude(intent.getDoubleExtra("userLat", 0));
            userLocation.setLongitude(intent.getDoubleExtra("userLon", 0));
        }

        // Get network connectivity client
        networkConnectivityClient = new NetworkConnectivityClient(this);

        // Set action bar
        setTitle(DisplayNameUtil.getLongDisplayName(this, selectedRoute));

        if (selectedRoute.getMode() != Route.BUS || SilverLine.isSilverLine(selectedRoute.getId())) {
            if (Build.VERSION.SDK_INT >= 21) {
                // Create color for status bar
                float[] hsv = new float[3];
                Color.colorToHSV(Color.parseColor(selectedRoute.getPrimaryColor()), hsv);
                hsv[2] *= .8f;

                // Set status bar color
                Window window = getWindow();
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
                window.setStatusBarColor(Color.HSVToColor(hsv));

                // Set action bar background color
                ActionBar actionBar = getSupportActionBar();
                if (actionBar != null) {
                    actionBar.setBackgroundDrawable(
                            new ColorDrawable(Color.parseColor(selectedRoute.getPrimaryColor())));
                }
            }
        }

        // Set the no predictions indicator
        noPredictionsView = findViewById(R.id.no_predictions_view);

        // Get error text view
        errorTextView = findViewById(R.id.error_message_text_view);

        // Get app bar and app bar params
        appBarLayout = findViewById(R.id.app_bar_layout);
        CoordinatorLayout.LayoutParams params =
                (CoordinatorLayout.LayoutParams) appBarLayout.getLayoutParams();
        params.height = (int) (getResources().getDisplayMetrics().heightPixels * .6);

        // Disable scrolling inside app bar
        AppBarLayout.Behavior behavior = new AppBarLayout.Behavior();
        behavior.setDragCallback(new AppBarLayout.Behavior.DragCallback() {
            @Override
            public boolean canDrag(@NonNull AppBarLayout appBarLayout) {
                return false;
            }
        });
        params.setBehavior(behavior);

        // Get and initialize map view
        mapView = findViewById(R.id.map_view);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        // Get map progress bar
        mapProgressBar = findViewById(R.id.map_progress_bar);

        // Get the stop selector view
        routeDetailSpinners = findViewById(R.id.route_detail_spinners);
        populateDirectionSpinner(selectedRoute.getAllDirections());

        // Populate the stops spinner with the nearest stop until we query the shapes
        if (selectedRoute.getFocusStop(selectedDirectionId) != null) {
            Stop[] selectedStopArray = {selectedRoute.getFocusStop(selectedDirectionId)};
            populateStopSpinner(selectedStopArray);
        }

        routeDetailSpinners.setOnDirectionSelectedListener(this);
        routeDetailSpinners.setOnStopSelectedListener(this);

        // Get and initialize swipe refresh layout
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        swipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(this,
                R.color.colorAccent));
        swipeRefreshLayout.setEnabled(false);

        // Get service alerts indicator
        serviceAlertsIndicatorView = findViewById(R.id.service_alerts_indicator_view);

        // Get recycler view
        recyclerView = findViewById(R.id.predictions_recycler_view);

        // Set recycler view layout
        recyclerView.setLayoutManager(new GridLayoutManager(this, 1));

        // Disable scrolling while activity is still initializing
        recyclerView.setNestedScrollingEnabled(false);

        // Add on scroll listener
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    userIsScrolling = false;

                    if (!dataRefreshing &&
                            !noPredictionsView.isError()) {
                        refreshPredictions(false);
                    }

                    if (!shapesLoaded) {
                        refreshShapes();
                    }

                } else if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    userIsScrolling = true;
                }
            }
        });

        // Create and set the recycler view adapter
        recyclerViewAdapter = new RouteSearchRecyclerViewAdapter();
        recyclerView.setAdapter(recyclerViewAdapter);

        // Set OnClickListener
        recyclerViewAdapter.setOnItemClickListener(new RouteSearchRecyclerViewAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(int position) {
                Prediction prediction = recyclerViewAdapter.getPrediction(position);

                if (prediction != null) {
                    Intent intent = new Intent(RouteDetailActivity.this, TripDetailActivity.class);
                    intent.putExtra("route", prediction.getRoute());
                    intent.putExtra("stop", prediction.getStop());
                    intent.putExtra("stopSequence", prediction.getStopSequence());
                    intent.putExtra("trip", prediction.getTripId());
                    intent.putExtra("name", prediction.getTripName());
                    intent.putExtra("destination", prediction.getDestination());
                    intent.putExtra("vehicle", prediction.getVehicleId());
                    intent.putExtra("date", prediction.getPredictionTime());
                    startActivity(intent);
                }
            }
        });

        // Refresh service alerts
        refreshServiceAlerts();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        gMap = googleMap;
        mapReady = true;

        // Move the map camera to the selected stop
        Stop stop = selectedRoute.getFocusStop(selectedDirectionId);
        LatLng latLng = (stop == null)
                ? new LatLng(userLocation.getLatitude(), userLocation.getLongitude())
                : new LatLng(stop.getLocation().getLatitude(), stop.getLocation().getLongitude());
        gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, DEFAULT_MAP_FAR_ZOOM_LEVEL));

        // Set the map style
        gMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style));

        // Set the map UI settings
        UiSettings mapUiSettings = gMap.getUiSettings();
        mapUiSettings.setRotateGesturesEnabled(false);
        mapUiSettings.setTiltGesturesEnabled(false);
        mapUiSettings.setZoomControlsEnabled(true);

        // Enable map location UI features
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            gMap.setMyLocationEnabled(true);
            mapUiSettings.setMyLocationButtonEnabled(true);
        }

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
                    Stop selectedStop = (Stop) marker.getTag();

                    routeDetailSpinners.selectStop(selectedStop.getId());

                } else if (marker.getTag() instanceof Vehicle) {
                    if (selectedVehicleMarker != null)
                        selectedVehicleMarker.hideInfoWindow();

                    selectedVehicleMarker = marker;

                    selectedVehicleMarker.showInfoWindow();
                }

                return false;
            }
        });
        gMap.setOnMyLocationClickListener(new GoogleMap.OnMyLocationClickListener() {
            @Override
            public void onMyLocationClick(@NonNull Location location) {
                gMap.animateCamera(CameraUpdateFactory.newLatLng(
                        new LatLng(location.getLatitude(), location.getLongitude())));
            }
        });

        // Load route shapes
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

        // Refresh the activity to update UI so that the predictions are accurate
        // as of the last update
        //refreshPredictions(false);
        refreshVehicles();

        // Get the route shapes if there aren't any
        if (polylines.size() == 0 && mapReady) {
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
        timer.schedule(new VehiclesUpdateTimerTask(), 0, VEHICLES_UPDATE_RATE);
        timer.schedule(new ServiceAlertsUpdateTimerTask(), 0, SERVICE_ALERTS_UPDATE_RATE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();

        dataRefreshing = false;

        swipeRefreshLayout.setRefreshing(false);

        if (timer != null) {
            timer.cancel();
        }

        cancelUpdate();
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
    protected void onSaveInstanceState(Bundle outState) {
        outState.putSerializable("route", selectedRoute);
        outState.putInt("direction", selectedDirectionId);
        outState.putLong("refreshTime", refreshTime);

        super.onSaveInstanceState(outState);
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

                    selectedRoute.clearPredictions(Direction.INBOUND);
                    selectedRoute.clearPredictions(Direction.OUTBOUND);

                    clearVehicleMarkers();
                    clearPredictions();

                    enableOnErrorView(getResources().getString(R.string.network_error_text));

                } else if (errorManager.hasTimeZoneMismatch()) {
                    errorTextView.setText(R.string.time_zone_warning);
                    errorTextView.setVisibility(View.VISIBLE);

                } else if (!errorManager.hasNetworkError()) {
                    errorTextView.setVisibility(View.GONE);
                    swipeRefreshLayout.setRefreshing(true);
                    forceUpdate();
                }
            }
        });
    }

    private void getPredictions() {
        if (selectedRoute.getFocusStop(selectedDirectionId) != null) {
            if (networkConnectivityClient.isConnected()) {
                errorManager.setNetworkError(false);

                dataRefreshing = true;

                if (predictionsAsyncTask != null) {
                    predictionsAsyncTask.cancel(true);
                }

                predictionsAsyncTask = new PredictionsRouteSearchAsyncTask(realTimeApiKey, selectedRoute,
                        selectedDirectionId, new PredictionsPostExecuteListener());
                predictionsAsyncTask.execute();

            } else {
                errorManager.setNetworkError(true);
                enableOnErrorView(getResources().getString(R.string.error_network));
                dataRefreshing = false;
                swipeRefreshLayout.setRefreshing(false);
            }
        } else {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    refreshPredictions(true);
                }
            });
        }
    }

    private void getShapes() {
        // Hard coding to save the user time and data
        if (selectedRoute.getMode() == Route.HEAVY_RAIL || selectedRoute.getMode() == Route.LIGHT_RAIL) {
            if (BlueLine.isBlueLine(selectedRoute.getId())) {
                selectedRoute.addShapes(getShapesFromJson(R.raw.shapes_blue));

            } else if (OrangeLine.isOrangeLine(selectedRoute.getId())) {
                selectedRoute.addShapes(getShapesFromJson(R.raw.shapes_orange));

            } else if (RedLine.isRedLine(selectedRoute.getId()) && !RedLine.isMattapanLine(selectedRoute.getId())) {
                selectedRoute.addShapes(getShapesFromJson(R.raw.shapes_red));

            } else if (RedLine.isRedLine(selectedRoute.getId()) && RedLine.isMattapanLine(selectedRoute.getId())) {
                selectedRoute.addShapes(getShapesFromJson(R.raw.shapes_mattapan));

            } else if (GreenLine.isGreenLine(selectedRoute.getId())) {
                if (GreenLineCombined.isGreenLineCombined(selectedRoute.getId())) {
                    selectedRoute.addShapes(getShapesFromJson(R.raw.shapes_green_combined));
                } else if (GreenLine.isGreenLineB(selectedRoute.getId())) {
                    selectedRoute.addShapes(getShapesFromJson(R.raw.shapes_green_b));

                } else if (GreenLine.isGreenLineC(selectedRoute.getId())) {
                    selectedRoute.addShapes(getShapesFromJson(R.raw.shapes_green_c));

                } else if (GreenLine.isGreenLineD(selectedRoute.getId())) {
                    selectedRoute.addShapes(getShapesFromJson(R.raw.shapes_green_d));

                } else if (GreenLine.isGreenLineE(selectedRoute.getId())) {
                    selectedRoute.addShapes(getShapesFromJson(R.raw.shapes_green_e));

                }
            }

            refreshShapes();
            populateStopSpinner(selectedRoute.getStops(selectedDirectionId));

        } else if (networkConnectivityClient.isConnected()) {
            errorManager.setNetworkError(false);

            if (shapesAsyncTask != null) {
                shapesAsyncTask.cancel(true);
            }

            shapesAsyncTask = new ShapesAsyncTask(
                    realTimeApiKey, selectedRoute.getId(), new ShapesPostExecuteListener());
            shapesAsyncTask.execute();

        } else {
            errorManager.setNetworkError(true);
        }
    }

    private void getVehicles() {
        if (networkConnectivityClient.isConnected()) {
            errorManager.setNetworkError(false);

            if (vehiclesAsyncTask != null) {
                vehiclesAsyncTask.cancel(true);
            }

            vehiclesAsyncTask = new VehiclesByRouteAsyncTask(
                    realTimeApiKey, selectedRoute.getId(), new VehiclesPostExecuteListener());
            vehiclesAsyncTask.execute();

        } else {
            errorManager.setNetworkError(true);
        }
    }

    private void getServiceAlerts() {
        if (selectedRoute != null) {
            if (networkConnectivityClient.isConnected()) {
                errorManager.setNetworkError(false);

                if (serviceAlertsAsyncTask != null) {
                    serviceAlertsAsyncTask.cancel(true);
                }

                String[] routeId = {selectedRoute.getId()};

                serviceAlertsAsyncTask = new ServiceAlertsAsyncTask(
                        realTimeApiKey, routeId, new ServiceAlertsPostExecuteListener());
                serviceAlertsAsyncTask.execute();

            } else {
                errorManager.setNetworkError(true);
            }
        }
    }

    private void refreshPredictions(boolean returnToTop) {
        if (!userIsScrolling) {
            if (selectedRoute.getFocusStop(selectedDirectionId) != null) {
                recyclerViewAdapter.setPredictions(selectedRoute.getPredictions(selectedDirectionId));
                swipeRefreshLayout.setRefreshing(false);
                clearOnErrorView();

                if (recyclerViewAdapter.getItemCount() == 0) {
                    enableNoPredictionsView(getResources().getString(R.string.no_departures));

                } else {
                    clearOnErrorView();
                    noPredictionsView.clearNoPredictions();
                    recyclerView.setNestedScrollingEnabled(true);

                    if (returnToTop) {
                        recyclerView.scrollToPosition(0);
                    }
                }
            }
        }
    }

    private void refreshShapes() {
        if (!userIsScrolling) {
            clearShapes();

            Stop selectedStop = selectedRoute.getFocusStop(selectedDirectionId);

            for (Shape shape : selectedRoute.getShapes(selectedDirectionId)) {
                if (shape.getPriority() >= 0 && shape.getStops().length > 0) {
                    // Draw the polyline
                    polylines.addAll(Arrays.asList(drawPolyline(shape)));

                    // Draw each stop that hasn't been drawn yet
                    for (Stop stop : shape.getStops()) {
                        if (!stopMarkers.containsKey(stop.getId())) {
                            // Draw the stop marker
                            Marker currentMarker = drawStopMarker(stop);

                            // Use selected stop marker if this stop is the selected stop
                            if (selectedStop != null && selectedStopMarker == null &&
                                    (selectedStop.equals(stop) ||
                                            selectedStop.isParentOf(stop.getId()) ||
                                            stop.isParentOf(selectedStop.getId()))) {
                                selectedStopMarker = currentMarker;
                                selectedStopMarker.setIcon(BitmapDescriptorFactory
                                        .fromResource(R.drawable.icon_selected_stop));
                            }

                            // Add stop to the drawn stops hash map
                            stopMarkers.put(stop.getId(), currentMarker);
                        }
                    }
                }
            }

            // If the selected stop is not a stop included in the shape objects
            if (selectedStopMarker == null && selectedStop != null) {
                selectedStopMarker = drawStopMarker(selectedStop);
                selectedStopMarker.setIcon(BitmapDescriptorFactory
                        .fromResource(R.drawable.icon_selected_stop));
                stopMarkers.put(selectedStop.getId(), selectedStopMarker);
            }

            mapProgressBar.setVisibility(View.GONE);
            shapesLoaded = true;
        }
    }

    private void refreshVehicles() {
        if (!userIsScrolling && mapReady) {
            ArrayList<String> trackedVehicleIds = new ArrayList<>();
            ArrayList<String> expiredVehicleIds = new ArrayList<>();

            // Get all vehicles moving in selected direction
            for (Vehicle vehicle : vehicles.values()) {
                trackedVehicleIds.add(vehicle.getId());
            }

            // Find the currently displayed vehicles that are no longer being tracked/now expired
            for (String vehicleId : vehicleMarkers.keySet()) {
                if (!trackedVehicleIds.contains(vehicleId))
                    expiredVehicleIds.add(vehicleId);
            }

            // Removed the expired vehicles
            for (String vehicleId : expiredVehicleIds) {
                vehicleMarkers.get(vehicleId).remove();
                vehicleMarkers.remove(vehicleId);
            }

            for (Vehicle vehicle : vehicles.values()) {
                Marker vMarker = vehicleMarkers.get(vehicle.getId());
                if (vMarker != null) {
                    vMarker.setPosition(new LatLng(
                            vehicle.getLocation().getLatitude(),
                            vehicle.getLocation().getLongitude()));
                    vMarker.setRotation(vehicle.getLocation().getBearing());

                    if (vehicle.getDestination() != null) {
                        vMarker.setSnippet("To " + vehicle.getDestination());
                    }
                } else {
                    String vehicleTitle;
                    int mode = selectedRoute.getMode();
                    if (mode == Route.COMMUTER_RAIL) {
                        vehicleTitle = getResources().getString(R.string.train) + " " + vehicle.getTripName();
                    } else if (mode == Route.LIGHT_RAIL || mode == Route.HEAVY_RAIL) {
                        vehicleTitle = getResources().getString(R.string.train) + " " + vehicle.getLabel();
                    } else {
                        vehicleTitle = getResources().getString(R.string.vehicle) + " " + vehicle.getLabel();
                    }
                    vehicleMarkers.put(vehicle.getId(), drawVehicleMarker(vehicle, vehicleTitle));
                }
            }
        }
    }

    private void refreshServiceAlerts() {
        if (selectedRoute != null) {
            if (!userIsScrolling) {
                if (selectedRoute.getServiceAlerts().size() > 0) {
                    serviceAlertsIndicatorView.setServiceAlerts(selectedRoute);
                    serviceAlertsIndicatorView.setVisibility(View.VISIBLE);

                } else {
                    serviceAlertsIndicatorView.setVisibility(View.GONE);
                }
            }
        }
    }

    private void enableOnErrorView(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                recyclerViewAdapter.clear();
                recyclerView.setNestedScrollingEnabled(false);
                swipeRefreshLayout.setRefreshing(false);
                appBarLayout.setExpanded(true);

                noPredictionsView.setError(message);
            }
        });
    }

    private void enableNoPredictionsView(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                recyclerView.setNestedScrollingEnabled(false);
                swipeRefreshLayout.setRefreshing(false);
                appBarLayout.setExpanded(true);

                if (loaded) {
                    noPredictionsView.setNoPredictions(message);
                }
            }
        });
    }

    private void clearOnErrorView() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                noPredictionsView.clearError();
            }
        });

    }

    private void clearPredictions() {
        recyclerViewAdapter.clear();
        appBarLayout.setExpanded(true);
    }

    private void clearShapes() {
        clearPolylines();
        clearStopMarkers();
    }

    private void clearPolylines() {
        for (Polyline pl : polylines) {
            pl.remove();
        }
        polylines.clear();
    }

    private void clearStopMarkers() {
        for (Marker m : stopMarkers.values()) {
            m.remove();
        }
        stopMarkers.clear();

        if (selectedStopMarker != null) {
            selectedStopMarker.remove();
            selectedStopMarker = null;
        }
    }

    private void clearVehicleMarkers() {
        for (Marker vm : vehicleMarkers.values()) {
            vm.remove();
        }
        vehicleMarkers.clear();
    }

    private Shape[] getShapesFromJson(int jsonFile) {
        return ShapesJsonParser.parse(RawResourceReader.toString(getResources().openRawResource(jsonFile)));
    }

    private Polyline[] drawPolyline(@NonNull Shape shape) {
        List<LatLng> shapeCoordinates = PolyUtil.decode(shape.getPolyline());

        Polyline[] polylines = {
                gMap.addPolyline(new PolylineOptions()
                        .addAll(shapeCoordinates)
                        .color(Color.parseColor(selectedRoute.getPrimaryColor()))
                        .zIndex(1)
                        .jointType(JointType.ROUND)
                        .startCap(new RoundCap())
                        .endCap(new RoundCap())
                        .width(8)),

                gMap.addPolyline(new PolylineOptions()
                        .addAll(shapeCoordinates)
                        .color(Color.parseColor("#FFFFFF"))
                        .zIndex(0)
                        .jointType(JointType.ROUND)
                        .startCap(new RoundCap())
                        .endCap(new RoundCap())
                        .width(14))};

        return polylines;
    }

    private Marker drawStopMarker(@NonNull Stop stop) {
        MarkerOptions markerOptions = selectedRoute.getStopMarkerOptions();

        markerOptions.position(new LatLng(
                stop.getLocation().getLatitude(), stop.getLocation().getLongitude()));
        markerOptions.zIndex(10);
        markerOptions.title(stop.getName());

        Marker stopMarker = gMap.addMarker(markerOptions);

        stopMarker.setTag(stop);

        return stopMarker;
    }

    private Marker drawVehicleMarker(@NonNull Vehicle vehicle, String vehicleTitle) {
        Marker vehicleMarker = gMap.addMarker(new MarkerOptions()
                .position(new LatLng(
                        vehicle.getLocation().getLatitude(), vehicle.getLocation().getLongitude()))
                .rotation(vehicle.getLocation().getBearing())
                .anchor(0.5f, 0.5f)
                .zIndex(20)
                .flat(true)
                .title(vehicleTitle)
                //.title(getResources().getString(R.string.vehicle) + " " + vehicle.getLabel())
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.icon_vehicle))
        );

        if (vehicle.getDestination() != null) {
            vehicleMarker.setSnippet("To " + vehicle.getDestination());
        }

        vehicleMarker.setTag(vehicle);

        return vehicleMarker;
    }

    private void populateDirectionSpinner(Direction[] directions) {
        if (directions[0].getId() == Direction.SOUTHBOUND) {
            Direction d = directions[0];
            directions[0] = directions[1];
            directions[1] = d;
        }

        routeDetailSpinners.populateDirectionSpinner(directions);
        routeDetailSpinners.selectDirection(selectedDirectionId);
    }

    private void populateStopSpinner(Stop[] stops) {
        ArrayList<Stop> stopsList = new ArrayList<>(Arrays.asList(stops));
        Stop selectedStop = selectedRoute.getFocusStop(selectedDirectionId);

        if (selectedStop != null && !stopsList.contains(selectedStop)) {
            stopsList.add(selectedStop);
            stops = stopsList.toArray(new Stop[0]);
        }

        routeDetailSpinners.populateStopSpinner(stops);

        if (selectedStop != null) {
            routeDetailSpinners.selectStop(selectedStop.getId());
        } else if (stops.length > 0 && userLocation.getLatitude() != 0 &&
                userLocation.getLongitude() != 0) {
            // Locate stop nearest to user's location
            Stop nearestStop = stops[0];
            double nearestDistance = stops[0].getLocation().distanceTo(userLocation);

            for (int i = 1; i < stops.length; i++) {
                double d = stops[i].getLocation().distanceTo(userLocation);
                if (stops[i].getLocation().distanceTo(userLocation) < nearestDistance) {
                    nearestStop = stops[i];
                    nearestDistance = d;
                }
            }

            routeDetailSpinners.selectStop(nearestStop.getId());
        }
    }

    @Override
    public void onDirectionSelected(Direction selectedDirection) {
        selectedDirectionId = selectedDirection.getId();

        clearPredictions();
        clearOnErrorView();

        populateStopSpinner(selectedRoute.getStops(selectedDirectionId));

        if (shapesLoaded) {
            refreshShapes();
            refreshVehicles();
        }
    }

    @Override
    public void onStopSelected(Stop selectedStop) {
        // Otherwise set the nearest stop to the selected stop
        selectedRoute.setFocusStop(selectedDirectionId, selectedStop);

        // Find the nearest stop in the opposite direction
        Stop nearestOppositeStop = null;
        float oppositeStopDistance = 0;
        int oppositeDirectionId = (selectedDirectionId + 1) % 2;

        for (Stop s : selectedRoute.getStops(oppositeDirectionId)) {
            float dist = s.getLocation().distanceTo(selectedStop.getLocation());
            if (nearestOppositeStop == null || dist < oppositeStopDistance) {
                nearestOppositeStop = s;
                oppositeStopDistance = dist;
            }
        }

        selectedRoute.setFocusStop(oppositeDirectionId, nearestOppositeStop);

        // Get the predictions for the selected stop
        clearPredictions();
        clearOnErrorView();
        swipeRefreshLayout.setRefreshing(true);
        getPredictions();


        // Update the stop markers on the map
        if (selectedStopMarker != null) {
            selectedStopMarker.setIcon(selectedRoute.getStopMarkerIcon());
        }

        if (mapReady) {
            selectedStopMarker = stopMarkers.get(selectedStop.getId());

            if (selectedStopMarker == null) {
                selectedStopMarker = drawStopMarker(selectedStop);
                stopMarkers.put(selectedStop.getId(), selectedStopMarker);
            }

            selectedStopMarker.setIcon(
                    BitmapDescriptorFactory.fromResource(R.drawable.icon_selected_stop));
            selectedStopMarker.showInfoWindow();

            // Center the map on the selected stop
            gMap.animateCamera(CameraUpdateFactory.newLatLng(selectedStopMarker.getPosition()));
        }
    }

    private void backgroundUpdate() {
        if (!dataRefreshing) {
            getPredictions();
        }
    }

    private void forceUpdate() {
        getPredictions();
    }

    private void cancelUpdate() {
        if (predictionsAsyncTask != null) {
            predictionsAsyncTask.cancel(true);
        }

        if (shapesAsyncTask != null) {
            shapesAsyncTask.cancel(true);
        }

        if (vehiclesAsyncTask != null) {
            vehiclesAsyncTask.cancel(true);
        }

        if (serviceAlertsAsyncTask != null) {
            serviceAlertsAsyncTask.cancel(true);
        }
    }

    private class PredictionsPostExecuteListener implements PredictionsRouteSearchAsyncTask.OnPostExecuteListener {
        @Override
        public void onSuccess(List<Prediction> predictions) {
            dataRefreshing = false;
            refreshTime = new Date().getTime();
            loaded = true;

            Calendar calendar = Calendar.getInstance();
            calendar.setTime(new Date());
            int today = calendar.get(Calendar.DAY_OF_MONTH);

            // Clear old predictions
            selectedRoute.clearPredictions(0);
            selectedRoute.clearPredictions(1);

            for (Prediction p : predictions) {
                Vehicle vt = vehicleTrips.get(p.getTripId());
                int pDay = -1;
                if (p.getPredictionTime() != null) {
                    calendar.setTime(p.getPredictionTime());
                    pDay = calendar.get(Calendar.DAY_OF_MONTH);
                }

                if (selectedRoute.getMode() != Route.BUS || vt == null || pDay != today ||
                        (p.getVehicle() != null &&
                                vt.getCurrentStopSequence() <= p.getStopSequence())) {
                    // Reduce 'time bounce' by replacing current prediction time with prior prediction
                    // time if one exists if they are within one minute
                    pastData.normalizePrediction(p);

                    // Set vehicle for predictions
                    Vehicle v = vehicles.get(p.getVehicleId());
                    if (v != null) {
                        p.setVehicle(v);
                    }

                    // Put this prediction into list of prior predictions
                    pastData.add(p);

                    // Add prediction to route
                    if (p.getCountdownTime() > -60000) {
                        selectedRoute.addPrediction(p);
                    }
                }
            }

            refreshPredictions(false);
        }

        @Override
        public void onError() {
            dataRefreshing = false;
            refreshTime = new Date().getTime();
            enableOnErrorView(getResources().getString(R.string.error_upcoming_predictions));

            selectedRoute.clearPredictions(0);
            selectedRoute.clearPredictions(1);

            refreshPredictions(true);
        }
    }

    private class ShapesPostExecuteListener implements ShapesAsyncTask.OnPostExecuteListener {
        @Override
        public void onSuccess(Shape[] shapes) {
            selectedRoute.addShapes(shapes);

            refreshShapes();
            populateStopSpinner(selectedRoute.getStops(selectedDirectionId));
        }

        @Override
        public void onError() {
            enableOnErrorView(getResources().getString(R.string.error_stops));
            getShapes();
        }
    }

    private class VehiclesPostExecuteListener implements VehiclesByRouteAsyncTask.OnPostExecuteListener {
        @Override
        public void onSuccess(Vehicle[] vs) {
            vehicles.clear();
            vehicleTrips.clear();

            for (Vehicle v : vs) {
                vehicles.put(v.getId(), v);
                vehicleTrips.put(v.getTripId(), v);
            }

            ArrayList<Prediction> predictions = selectedRoute.getPredictions(0);
            predictions.addAll(selectedRoute.getPredictions(1));

            if (predictions.size() > 0) {
                for (Prediction p : predictions) {
                    Vehicle v = vehicles.get(p.getVehicleId());
                    if (v != null) {
                        p.setVehicle(v);
                    }
                }

                refreshPredictions(false);
            }

            refreshVehicles();
        }

        @Override
        public void onError() {
        }
    }

    private class ServiceAlertsPostExecuteListener implements ServiceAlertsAsyncTask.OnPostExecuteListener {
        @Override
        public void onSuccess(ServiceAlert[] serviceAlerts) {
            selectedRoute.clearServiceAlerts();
            selectedRoute.addAllServiceAlerts(serviceAlerts);

            refreshServiceAlerts();
        }

        @Override
        public void onError() {
            getServiceAlerts();
        }
    }

    private class PredictionsUpdateTimerTask extends TimerTask {
        @Override
        public void run() {
            backgroundUpdate();
        }
    }

    private class VehiclesUpdateTimerTask extends TimerTask {
        @Override
        public void run() {
            getVehicles();
        }
    }

    private class ServiceAlertsUpdateTimerTask extends TimerTask {
        @Override
        public void run() {
            getServiceAlerts();
        }
    }
}
