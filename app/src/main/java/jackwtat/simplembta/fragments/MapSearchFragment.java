package jackwtat.simplembta.fragments;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CoordinatorLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

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
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap;
import com.google.maps.android.PolyUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import jackwtat.simplembta.activities.MainActivity;
import jackwtat.simplembta.activities.RouteDetailActivity;
import jackwtat.simplembta.adapters.MapSearchRecyclerViewAdapter;
import jackwtat.simplembta.adapters.MapSearchRecyclerViewAdapter.OnItemClickListener;
import jackwtat.simplembta.asyncTasks.PredictionsAsyncTask;
import jackwtat.simplembta.asyncTasks.PredictionsByStopsAsyncTask;
import jackwtat.simplembta.asyncTasks.RoutesByStopsAsyncTask;
import jackwtat.simplembta.asyncTasks.SchedulesAsyncTask;
import jackwtat.simplembta.asyncTasks.ServiceAlertsAsyncTask;
import jackwtat.simplembta.asyncTasks.StopsByLocationAsyncTask;
import jackwtat.simplembta.clients.LocationClient;
import jackwtat.simplembta.clients.NetworkConnectivityClient;
import jackwtat.simplembta.map.markers.StopMarkerFactory;
import jackwtat.simplembta.model.Direction;
import jackwtat.simplembta.model.Prediction;
import jackwtat.simplembta.model.ServiceAlert;
import jackwtat.simplembta.model.routes.BlueLine;
import jackwtat.simplembta.model.routes.CommuterRail;
import jackwtat.simplembta.model.routes.CommuterRailNorthSide;
import jackwtat.simplembta.model.routes.CommuterRailOldColony;
import jackwtat.simplembta.model.routes.CommuterRailSouthSide;
import jackwtat.simplembta.model.routes.Ferry;
import jackwtat.simplembta.model.routes.GreenLine;
import jackwtat.simplembta.model.routes.GreenLineCombined;
import jackwtat.simplembta.model.routes.OrangeLine;
import jackwtat.simplembta.model.routes.RedLine;
import jackwtat.simplembta.model.routes.Route;
import jackwtat.simplembta.model.Shape;
import jackwtat.simplembta.model.Stop;
import jackwtat.simplembta.model.routes.SilverLine;
import jackwtat.simplembta.model.routes.SilverLineCombined;
import jackwtat.simplembta.utilities.DisplayNameUtil;
import jackwtat.simplembta.utilities.ErrorManager;
import jackwtat.simplembta.R;
import jackwtat.simplembta.utilities.RawResourceReader;
import jackwtat.simplembta.jsonParsers.ShapesJsonParser;
import jackwtat.simplembta.views.ServiceAlertsListView;
import jackwtat.simplembta.views.ServiceAlertsTitleView;

public class MapSearchFragment extends Fragment implements OnMapReadyCallback,
        ErrorManager.OnErrorChangedListener {
    public static final String LOG_TAG = "MapSearchFragment";

    // Maximum age of prediction
    public static final long MAX_PREDICTION_AGE = 90000;

    // Predictions auto update rate
    public static final long PREDICTIONS_UPDATE_RATE = 15000;

    // Location auto update rate
    public static final long LOCATION_UPDATE_RATE = 1000;

    // Time between location client updates
    public static final long LOCATION_CLIENT_INTERVAL = 500;

    // Fastest time between location updates
    public static final long FASTEST_LOCATION_CLIENT_INTERVAL = 250;

    // Time since last onStop() before restarting the location
    public static final long LOCATION_UPDATE_RESTART_TIME = 180000;

    // Default level of zoom for the map
    public static final int DEFAULT_MAP_ZOOM_LEVEL = 16;

    // Zoom level where stop markers become visible
    public static final int STOP_MARKER_VISIBILITY_LEVEL = 15;

    // Zoom level where key stop markers become visible
    public static final int KEY_STOP_MARKER_VISIBILITY_LEVEL = 12;

    // Zoom level where commuter rail stop markers become visible
    public static final int COMMUTER_STOP_MARKER_VISIBILITY_LEVEL = 10;

    // Distance in meters from last target location before target location can be updated
    public static final int DISTANCE_TO_TARGET_LOCATION_UPDATE = 50;

    // Distance in meters from last target location before visible refresh
    public static final int DISTANCE_TO_FORCE_REFRESH = 200;

    // Map interaction statuses
    public static final int USER_HAS_NOT_MOVED_MAP = 0;
    public static final int USER_HAS_MOVED_MAP = 1;

    private MainActivity mainActivity;

    private View rootView;
    private AppBarLayout appBarLayout;
    private MapView mapView;
    private ImageView mapTargetView;
    private GoogleMap gMap;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private TextView noPredictionsTextView;

    private String realTimeApiKey;
    private NetworkConnectivityClient networkConnectivityClient;
    private LocationClient locationClient;
    private MapSearchRecyclerViewAdapter recyclerViewAdapter;
    private ErrorManager errorManager;
    private Timer timer;

    private StopsByLocationAsyncTask stopsAsyncTask;
    private RoutesByStopsAsyncTask routesAsyncTask;
    private PredictionsAsyncTask predictionsAsyncTask;
    private ServiceAlertsAsyncTask serviceAlertsAsyncTask;

    private boolean refreshing = false;
    private boolean mapReady = false;
    private boolean cameraIsMoving = false;
    private boolean userIsScrolling = false;
    private boolean staleLocation = true;
    private int mapState = USER_HAS_NOT_MOVED_MAP;
    private int cameraMoveReason = GoogleMap.OnCameraMoveStartedListener.REASON_DEVELOPER_ANIMATION;
    private long refreshTime = 0;
    private long onPauseTime = 0;
    private int predictionsCount = 0;

    // Data surrounding the user's current location
    private Location userLocation = new Location("");

    // Data surrounding the targeted location
    private Location targetLocation = new Location("");
    private HashMap<String, Stop> targetStops = new HashMap<>();
    private HashMap<String, Route> targetRoutes = new HashMap<>();

    // Data surrounding the location displayed on the map
    private Location displayedLocation = new Location("");
    private HashMap<String, Stop> displayedStops = new HashMap<>();
    private HashMap<String, Marker> displayedStopMarkers = new HashMap<>();

    // Selected stop data
    private Stop selectedStop = null;
    private Marker selectedStopMarker = null;

    // Key stop/route data
    private HashMap<String, Marker> keyStopMarkers = new HashMap<>();
    private HashMap<String, Marker> commuterStopMarkers = new HashMap<>();
    private Route[] keyRoutes = {
            new BlueLine("Blue"),
            new OrangeLine("Orange"),
            new RedLine("Red"),
            new RedLine("Mattapan"),
            new GreenLineCombined(),
            new SilverLineCombined()};
    private Route[] commuterRoutes = {
            new CommuterRailNorthSide(),
            new CommuterRailSouthSide(),
            new CommuterRailOldColony(),
            new Ferry("Boat-F1"),
            new Ferry("Boat-F4")};


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get MBTA realTime API key
        realTimeApiKey = getContext().getString(R.string.v3_mbta_realtime_api_key);

        // Initialize network connectivity client
        networkConnectivityClient = new NetworkConnectivityClient(getContext());

        // Get the location the user last viewed
        SharedPreferences sharedPreferences = getContext().getSharedPreferences(
                getResources().getString(R.string.saved_map_search_latlon), Context.MODE_PRIVATE);
        targetLocation.setLatitude(sharedPreferences.getFloat("latitude", (float) 42.3604));
        targetLocation.setLongitude(sharedPreferences.getFloat("longitude", (float) -71.0580));

        LocationClient.requestLocationPermission(getActivity());
        locationClient = new LocationClient(getContext(), LOCATION_CLIENT_INTERVAL,
                FASTEST_LOCATION_CLIENT_INTERVAL);

        // Add the key routes shapes
        Shape[][] keyShapes = {
                getShapesFromJson(R.raw.shapes_blue),
                getShapesFromJson(R.raw.shapes_orange),
                getShapesFromJson(R.raw.shapes_red),
                getShapesFromJson(R.raw.shapes_green_combined),
                getShapesFromJson(R.raw.shapes_silver),
                getShapesFromJson(R.raw.shapes_mattapan)};
        for (Shape[] s : keyShapes) {
            for (Route r : keyRoutes) {
                if (r.equals(s[0].getRouteId())) {
                    r.setShapes(s);
                    break;
                }
            }
        }

        // Add the commuter rail route shapes
        Shape[][] commuterShapes = {
                getShapesFromJson(R.raw.shapes_commuter_north),
                getShapesFromJson(R.raw.shapes_commuter_south),
                getShapesFromJson(R.raw.shapes_commuter_old_colony),
                getShapesFromJson(R.raw.shapes_boat_f1),
                getShapesFromJson(R.raw.shapes_boat_f4)};
        for (Shape[] s : commuterShapes) {
            for (Route r : commuterRoutes) {
                if (r.equals(s[0].getRouteId())) {
                    r.setShapes(s);
                    break;
                }
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_map_search, container, false);

        // Get app bar and app bar params
        appBarLayout = rootView.findViewById(R.id.app_bar_layout);
        CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) appBarLayout.getLayoutParams();

        // Set app bar height
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

        // Get and initialize map view
        mapView = rootView.findViewById(R.id.map_view);
        mapView.getLayoutParams().height =
                (int) (getResources().getDisplayMetrics().heightPixels * .6);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        // Get the map target view
        mapTargetView = rootView.findViewById(R.id.map_target_view);

        // Get and initialize swipe refresh layout
        swipeRefreshLayout = rootView.findViewById(R.id.swipe_refresh_layout);
        swipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(getContext(),
                R.color.colorAccent));
        swipeRefreshLayout.setEnabled(false);

        // Initialize recycler view
        recyclerView = rootView.findViewById(R.id.predictions_recycler_view);

        // Set recycler view layout
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 1));

        // Disable scrolling while fragment is still initializing
        recyclerView.setNestedScrollingEnabled(false);

        // Add onScrollListener
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    userIsScrolling = false;

                    if (!refreshing)
                        refreshPredictionViews();
                } else if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    userIsScrolling = true;
                }
            }
        });

        // Set predictions adapter
        recyclerViewAdapter = new MapSearchRecyclerViewAdapter();
        recyclerView.setAdapter(recyclerViewAdapter);

        // Set OnClickListeners
        recyclerViewAdapter.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(int position) {
                Route route = recyclerViewAdapter.getAdapterItem(position).getRoute();
                int direction = recyclerViewAdapter.getAdapterItem(position).getDirection();

                if (mainActivity != null) {
                    Stop stop = route.getNearestStop(direction);

                    if (stop == null) {
                        mainActivity.goToRoute(route, direction, targetLocation);
                    } else {
                        mainActivity.goToRoute(route, direction, stop);
                    }

                } else {
                    Intent intent = new Intent(getActivity(), RouteDetailActivity.class);
                    intent.putExtra("route", route);
                    intent.putExtra("direction", direction);
                    intent.putExtra("refreshTime", refreshTime);
                    intent.putExtra("userLat", targetLocation.getLatitude());
                    intent.putExtra("userLon", targetLocation.getLongitude());
                    startActivity(intent);
                }
            }
        });

        // Set the no predictions indicator
        noPredictionsTextView = rootView.findViewById(R.id.no_predictions_text_view);

        return rootView;
    }

    @Override
    public void onMapReady(final GoogleMap googleMap) {
        gMap = googleMap;
        mapReady = true;

        // Move the map camera to the last known location
        gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                new LatLng(targetLocation.getLatitude(), targetLocation.getLongitude()),
                DEFAULT_MAP_ZOOM_LEVEL));

        // Set the map style
        gMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(getContext(), R.raw.map_style));

        // Set the map UI settings
        UiSettings mapUiSettings = gMap.getUiSettings();
        mapUiSettings.setRotateGesturesEnabled(false);
        mapUiSettings.setTiltGesturesEnabled(false);
        mapUiSettings.setZoomControlsEnabled(true);

        // Enable map location UI features
        if (ActivityCompat.checkSelfPermission(getContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            gMap.setMyLocationEnabled(true);
            mapUiSettings.setMyLocationButtonEnabled(true);
        } else {
            mapTargetView.setVisibility(View.VISIBLE);
        }

        // Draw the key routes
        for (int i = 0; i < keyRoutes.length; i++) {
            drawRouteShapes(keyRoutes[i], keyRoutes.length + commuterRoutes.length - i);
            drawKeyStopMarkers(keyRoutes[i]);
        }

        // Draw the commuter rail routes
        for (int i = 0; i < commuterRoutes.length; i++) {
            drawRouteShapes(commuterRoutes[i], commuterRoutes.length - i);
            drawCommuterRailStopMarkers(commuterRoutes[i]);
        }

        // Set the action listeners
        gMap.setOnCameraMoveStartedListener(new GoogleMap.OnCameraMoveStartedListener() {
            @Override
            public void onCameraMoveStarted(int reason) {
                cameraIsMoving = true;
                cameraMoveReason = reason;

                if (reason == REASON_GESTURE) {
                    mapState = USER_HAS_MOVED_MAP;

                    if (selectedStop == null) {
                        mapTargetView.setVisibility(View.VISIBLE);
                        clearSelectedStop();
                    }
                }
            }
        });
        gMap.setOnCameraIdleListener(new GoogleMap.OnCameraIdleListener() {
            @Override
            public void onCameraIdle() {
                if (cameraIsMoving) {
                    cameraIsMoving = false;

                    displayedLocation.setLatitude(gMap.getCameraPosition().target.latitude);
                    displayedLocation.setLongitude(gMap.getCameraPosition().target.longitude);

                    // If the user has moved the map, then force a predictions update
                    if (cameraMoveReason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE &&
                            mapState == USER_HAS_MOVED_MAP && selectedStop == null) {
                        targetLocation.setLatitude(displayedLocation.getLatitude());
                        targetLocation.setLongitude(displayedLocation.getLongitude());

                        swipeRefreshLayout.setRefreshing(true);

                        forceUpdate();

                    } else if (selectedStop != null) {
                        if (stopsAsyncTask == null ||
                                stopsAsyncTask.getStatus() != AsyncTask.Status.RUNNING) {

                            stopsAsyncTask = new StopsByLocationAsyncTask(
                                    realTimeApiKey, displayedLocation,
                                    new StopsByLocationAsyncTask.OnPostExecuteListener() {
                                        @Override
                                        public void onSuccess(Stop[] stops) {
                                            refreshStopMarkers(stops);
                                        }

                                        @Override
                                        public void onError() {
                                        }
                                    });

                            stopsAsyncTask.execute();
                        }
                    }

                    // If the user has changed the zoom level,
                    // then change the commuter rail stop marker visibilities
                    if (gMap.getCameraPosition().zoom >= COMMUTER_STOP_MARKER_VISIBILITY_LEVEL) {
                        for (Marker marker : commuterStopMarkers.values()) {
                            marker.setVisible(true);
                        }
                    } else {
                        for (Marker marker : commuterStopMarkers.values()) {
                            if (!marker.equals(selectedStopMarker))
                                marker.setVisible(false);
                        }
                    }

                    // If the user has changed the zoom level,
                    // then change key stop markers visibilities
                    if (gMap.getCameraPosition().zoom >= KEY_STOP_MARKER_VISIBILITY_LEVEL) {
                        for (Marker marker : keyStopMarkers.values()) {
                            marker.setVisible(true);
                        }
                    } else {
                        for (Marker marker : keyStopMarkers.values()) {
                            Stop stop = (Stop) marker.getTag();
                            if (!marker.equals(selectedStopMarker) &&
                                    !commuterStopMarkers.containsKey(stop.getId()))
                                marker.setVisible(false);
                        }
                    }

                    // If the user has changed the zoom level,
                    // then change stop markers visibilities
                    if (gMap.getCameraPosition().zoom >= STOP_MARKER_VISIBILITY_LEVEL) {
                        for (Marker marker : displayedStopMarkers.values()) {
                            marker.setVisible(true);
                        }
                    } else {
                        for (Marker marker : displayedStopMarkers.values()) {
                            if (!marker.equals(selectedStopMarker))
                                marker.setVisible(false);
                        }
                    }
                }
            }
        });
        gMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                if (marker.getTag() instanceof Stop) {
                    selectedStopMarker = marker;
                    selectedStopMarker.showInfoWindow();
                    selectedStop = (Stop) marker.getTag();

                    targetLocation.setLatitude(marker.getPosition().latitude);
                    targetLocation.setLongitude(marker.getPosition().longitude);

                    mapTargetView.setVisibility(View.GONE);

                    swipeRefreshLayout.setRefreshing(true);

                    forceUpdate();
                }

                return true;
            }
        });
        gMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                if (selectedStopMarker != null) {
                    if (mapState == USER_HAS_MOVED_MAP) {
                        targetLocation.setLatitude(gMap.getCameraPosition().target.latitude);
                        targetLocation.setLongitude(gMap.getCameraPosition().target.longitude);

                        mapTargetView.setVisibility(View.VISIBLE);
                    } else {
                        targetLocation.setLatitude(userLocation.getLatitude());
                        targetLocation.setLongitude(userLocation.getLongitude());

                        mapTargetView.setVisibility(View.GONE);
                    }

                    clearSelectedStop();
                    swipeRefreshLayout.setRefreshing(true);
                    forceUpdate();
                }
            }
        });
        gMap.setOnMyLocationClickListener(new GoogleMap.OnMyLocationClickListener() {
            @Override
            public void onMyLocationClick(@NonNull Location location) {
                mapState = USER_HAS_NOT_MOVED_MAP;

                targetLocation = location;

                clearSelectedStop();

                mapTargetView.setVisibility(View.GONE);

                swipeRefreshLayout.setRefreshing(true);

                forceUpdate();

                gMap.animateCamera(CameraUpdateFactory.newLatLng(
                        new LatLng(targetLocation.getLatitude(), targetLocation.getLongitude())));
            }
        });
        gMap.setOnMyLocationButtonClickListener(new GoogleMap.OnMyLocationButtonClickListener() {
            @Override
            public boolean onMyLocationButtonClick() {
                mapState = USER_HAS_NOT_MOVED_MAP;

                targetLocation = userLocation;

                clearSelectedStop();

                mapTargetView.setVisibility(View.GONE);

                swipeRefreshLayout.setRefreshing(true);

                forceUpdate();

                return false;
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        mapView.onStart();

        // Get error manager
        errorManager = ErrorManager.getErrorManager();
        errorManager.registerOnErrorChangeListener(this);
    }

    @Override
    public void onResume() {
        mapView.onResume();
        super.onResume();

        // Get the time
        Long onResumeTime = new Date().getTime();

        // If too much time has elapsed since last refresh, then clear the predictions
        if (onResumeTime - refreshTime > MAX_PREDICTION_AGE) {
            clearPredictions();
        }

        swipeRefreshLayout.setRefreshing(true);

        locationClient.connect();

        timer = new Timer();
        timer.schedule(new PredictionsUpdateTimerTask(), PREDICTIONS_UPDATE_RATE, PREDICTIONS_UPDATE_RATE);
        timer.schedule(new LocationUpdateTimerTask(), 0, LOCATION_UPDATE_RATE);

        boolean locationPermissionGranted = ActivityCompat.checkSelfPermission(getContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        staleLocation = (mapState == USER_HAS_NOT_MOVED_MAP && selectedStop == null) ||
                onResumeTime - onPauseTime > LOCATION_UPDATE_RESTART_TIME;

        if (locationPermissionGranted && staleLocation) {
            mapState = USER_HAS_NOT_MOVED_MAP;
            clearSelectedStop();

            if (mapReady)
                mapTargetView.setVisibility(View.GONE);

        } else {
            if (mapReady && mapState == USER_HAS_MOVED_MAP && selectedStop == null) {
                mapTargetView.setVisibility(View.VISIBLE);

            } else if (selectedStop != null) {
                targetLocation.setLatitude(selectedStop.getLocation().getLatitude());
                targetLocation.setLongitude(selectedStop.getLocation().getLongitude());

                if (mapReady)
                    gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                            new LatLng(targetLocation.getLatitude(), targetLocation.getLongitude()),
                            DEFAULT_MAP_ZOOM_LEVEL));
            }

            forceUpdate();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();

        cameraIsMoving = false;

        refreshing = false;

        cancelUpdate();

        if (timer != null) {
            timer.cancel();
        }

        locationClient.disconnect();

        onPauseTime = new Date().getTime();

        swipeRefreshLayout.setRefreshing(false);

        // Save the location the user last viewed
        SharedPreferences sharedPreferences = getContext().getSharedPreferences(
                getResources().getString(R.string.saved_map_search_latlon), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putFloat("latitude", (float) targetLocation.getLatitude());
        editor.putFloat("longitude", (float) targetLocation.getLongitude());
        editor.apply();
    }

    @Override
    public void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    public void onDestroy() {
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
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (errorManager.hasLocationPermissionDenied() || errorManager.hasLocationError() ||
                        errorManager.hasNetworkError()) {
                    if (targetRoutes != null) {
                        targetRoutes.clear();
                        forceUpdate();
                    }

                    if (mapReady) {
                        UiSettings mapUiSettings = gMap.getUiSettings();
                        mapUiSettings.setMyLocationButtonEnabled(false);
                        mapTargetView.setVisibility(View.VISIBLE);
                    }

                } else if (mapReady && ActivityCompat.checkSelfPermission(getContext(),
                        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    UiSettings mapUiSettings = gMap.getUiSettings();
                    gMap.setMyLocationEnabled(true);
                    mapUiSettings.setMyLocationButtonEnabled(true);

                    if (mapState == USER_HAS_NOT_MOVED_MAP || selectedStop != null) {
                        mapTargetView.setVisibility(View.GONE);
                    }

                    if (mapReady) {
                        locationClient.updateLocation(new MapSearchFragment.LocationClientCallbacks());
                    }
                }
            }
        });
    }

    public void setMainActivity(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
    }

    private void backgroundUpdate() {
        if (!refreshing && new Date().getTime() - refreshTime > PREDICTIONS_UPDATE_RATE) {
            update();
        }
    }

    private void forceUpdate() {
        cancelUpdate();
        update();
    }

    private void update() {
        predictionsCount = 0;

        if (networkConnectivityClient.isConnected()) {
            errorManager.setNetworkError(false);

            refreshing = true;

            refreshTime = new Date().getTime();

            getStops();
        } else {
            enableOnErrorView(getResources().getString(R.string.error_network));
            errorManager.setNetworkError(true);
            refreshing = false;

            targetStops.clear();
            targetRoutes.clear();
        }
    }

    private void cancelUpdate() {
        if (stopsAsyncTask != null)
            stopsAsyncTask.cancel(true);

        if (routesAsyncTask != null)
            routesAsyncTask.cancel(true);

        if (predictionsAsyncTask != null)
            predictionsAsyncTask.cancel(true);

        if (serviceAlertsAsyncTask != null)
            serviceAlertsAsyncTask.cancel(true);
    }

    private void getStops() {
        if (stopsAsyncTask != null)
            stopsAsyncTask.cancel(true);

        stopsAsyncTask = new StopsByLocationAsyncTask(realTimeApiKey, targetLocation,
                new StopsPostExecuteListener());

        stopsAsyncTask.execute();
    }

    private void getRoutes() {
        if (routesAsyncTask != null)
            routesAsyncTask.cancel(true);

        String[] stopIds = targetStops.keySet().toArray(new String[targetStops.size()]);

        routesAsyncTask = new RoutesByStopsAsyncTask(realTimeApiKey, stopIds,
                new RoutesPostExecuteListener());

        routesAsyncTask.execute();
    }

    private void getPredictions() {
        if (predictionsAsyncTask != null)
            predictionsAsyncTask.cancel(true);

        String[] stopIds = targetStops.keySet().toArray(new String[targetStops.size()]);

        predictionsAsyncTask = new PredictionsByStopsAsyncTask(realTimeApiKey, stopIds,
                new PredictionsPostExecuteListener());

        predictionsAsyncTask.execute();
    }

    private void getSchedules() {
        if (predictionsAsyncTask != null)
            predictionsAsyncTask.cancel(true);

        ArrayList<String> routeIds = new ArrayList<>();

        // We'll only query routes that don't already have live pick-ups in this direction
        // Light rail and heavy rail (Green, Red, Blue, and Orange Lines) on-time performances
        // are too erratic and unreliable for scheduled predictions to be reliable
        for (Route route : targetRoutes.values()) {
            if (route.getMode() != Route.LIGHT_RAIL && route.getMode() != Route.HEAVY_RAIL) {
                routeIds.add(route.getId());
            }
        }

        String[] stopIds = targetStops.keySet().toArray(new String[targetStops.size()]);

        predictionsAsyncTask = new SchedulesAsyncTask(realTimeApiKey,
                routeIds.toArray(new String[routeIds.size()]), stopIds,
                new PredictionsPostExecuteListener());

        predictionsAsyncTask.execute();
    }

    private void getServiceAlerts() {
        if (serviceAlertsAsyncTask != null)
            serviceAlertsAsyncTask.cancel(true);

        serviceAlertsAsyncTask = new ServiceAlertsAsyncTask(realTimeApiKey,
                targetRoutes.keySet().toArray(new String[targetRoutes.size()]),
                new ServiceAlertsPostExecuteListener());

        serviceAlertsAsyncTask.execute();
    }

    private void refreshStopMarkers(Stop[] stops) {
        HashMap<String, Void> newStopIds = new HashMap<>();
        for (Stop stop : stops) {
            newStopIds.put(stop.getId(), null);
        }

        String[] displayedStopIds = displayedStops.keySet().toArray(new String[displayedStops.size()]);

        // Remove existing stop markers that are not in the argument Stops array and are not selected
        for (String displayedStopId : displayedStopIds) {
            if (!newStopIds.containsKey(displayedStopId) &&
                    (selectedStop == null || !selectedStop.getId().equals(displayedStopId))) {
                displayedStops.remove(displayedStopId);
                displayedStopMarkers.get(displayedStopId).remove();
                displayedStopMarkers.remove(displayedStopId);
            }
        }

        // Display the new stops
        for (Stop stop : stops) {
            String stopId = stop.getId();
            if (!displayedStops.containsKey(stopId) && !keyStopMarkers.containsKey(stopId)
                    && !commuterStopMarkers.containsKey(stopId)) {
                displayedStops.put(stopId, stop);
                displayedStopMarkers.put(stopId, drawStopMarker(stop));
            }
        }
    }

    private void refreshPredictionViews() {
        if (!userIsScrolling && targetRoutes != null) {
            recyclerViewAdapter.setData(targetLocation,
                    targetRoutes.values().toArray(new Route[targetRoutes.size()]), selectedStop);
            swipeRefreshLayout.setRefreshing(false);

            if (recyclerViewAdapter.getItemCount() == 0) {
                noPredictionsTextView.setText(getResources().getString(R.string.no_nearby_services));
                noPredictionsTextView.setVisibility(View.VISIBLE);
                appBarLayout.setExpanded(true);
                recyclerView.setNestedScrollingEnabled(false);
            } else {
                noPredictionsTextView.setVisibility(View.GONE);
                recyclerView.setNestedScrollingEnabled(true);
            }
        }
    }

    private void enableOnErrorView(String message) {
        final String m = message;
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                recyclerViewAdapter.clear();

                recyclerView.setNestedScrollingEnabled(false);

                swipeRefreshLayout.setRefreshing(false);

                appBarLayout.setExpanded(true);

                noPredictionsTextView.setText(m);
                noPredictionsTextView.setVisibility(View.VISIBLE);
            }
        });
    }

    private void clearPredictions() {
        recyclerViewAdapter.clear();
        noPredictionsTextView.setVisibility(View.GONE);
        appBarLayout.setExpanded(true);
        recyclerView.setNestedScrollingEnabled(false);

        targetStops.clear();
        targetRoutes.clear();
    }

    private void clearSelectedStop() {
        if (selectedStopMarker != null) {
            selectedStopMarker.hideInfoWindow();
            selectedStopMarker = null;
            selectedStop = null;
        }
    }

    private Shape[] getShapesFromJson(int jsonFile) {
        return ShapesJsonParser.parse(RawResourceReader.toString(getResources().openRawResource(jsonFile)));
    }

    private void drawRouteShapes(Route route, int zIndex) {
        int lineWidth;
        int paddingWidth;

        if (route.getMode() == Route.COMMUTER_RAIL || route.getMode() == Route.FERRY) {
            lineWidth = 6;
            paddingWidth = 10;
        } else {
            lineWidth = 8;
            paddingWidth = 12;
        }

        for (Shape s : route.getShapes(0)) {

            List<LatLng> coordinates = PolyUtil.decode(s.getPolyline());

            // Draw white background/line padding
            gMap.addPolyline(new PolylineOptions()
                    .addAll(coordinates)
                    .color(Color.parseColor("#FFFFFF"))
                    .zIndex(0)
                    .jointType(JointType.ROUND)
                    .startCap(new RoundCap())
                    .endCap(new RoundCap())
                    .width(paddingWidth));

            // Draw primary polyline
            gMap.addPolyline(new PolylineOptions()
                    .addAll(coordinates)
                    .color(Color.parseColor(route.getPrimaryColor()))
                    .zIndex(zIndex)
                    .jointType(JointType.ROUND)
                    .startCap(new RoundCap())
                    .endCap(new RoundCap())
                    .width(lineWidth));
        }
    }

    private void drawKeyStopMarkers(Route route) {
        HashMap<String, Marker> markers = new HashMap<>();

        for (Shape shape : route.getAllShapes()) {
            for (Stop stop : shape.getStops()) {
                if (keyStopMarkers.containsKey(stop.getId())) {
                    keyStopMarkers.get(stop.getId()).setIcon(
                            BitmapDescriptorFactory.fromResource(R.drawable.icon_stop));
                } else if (!markers.containsKey(stop.getId())) {
                    Marker stopMarker = gMap.addMarker(route.getStopMarkerOptions()
                            .position(new LatLng(
                                    stop.getLocation().getLatitude(),
                                    stop.getLocation().getLongitude()))
                            .zIndex(20)
                            .title(stop.getName()));

                    stopMarker.setTag(stop);

                    markers.put(stop.getId(), stopMarker);
                }
            }
        }

        keyStopMarkers.putAll(markers);
    }

    private void drawCommuterRailStopMarkers(Route route) {
        HashMap<String, Marker> markers = new HashMap<>();

        for (Shape shape : route.getAllShapes()) {
            for (Stop stop : shape.getStops()) {
                if (keyStopMarkers.containsKey(stop.getId())) {
                    Marker marker = keyStopMarkers.get(stop.getId());
                    marker.setIcon(
                            BitmapDescriptorFactory.fromResource(R.drawable.icon_stop));
                    markers.put(stop.getId(), marker);

                } else if (!markers.containsKey(stop.getId())) {
                    Marker stopMarker = gMap.addMarker(route.getStopMarkerOptions()
                            .position(new LatLng(
                                    stop.getLocation().getLatitude(),
                                    stop.getLocation().getLongitude()))
                            .zIndex(20)
                            .title(stop.getName()));

                    stopMarker.setTag(stop);

                    markers.put(stop.getId(), stopMarker);
                }
            }
        }

        commuterStopMarkers.putAll(markers);
    }

    private Marker drawStopMarker(Stop stop) {
        Marker stopMarker = gMap.addMarker(new StopMarkerFactory().createMarkerOptions()
                .position(new LatLng(
                        stop.getLocation().getLatitude(),
                        stop.getLocation().getLongitude()))
                .zIndex(20)
                .title(stop.getName()));

        stopMarker.setTag(stop);

        stopMarker.setVisible(gMap.getCameraPosition().zoom >= STOP_MARKER_VISIBILITY_LEVEL);

        return stopMarker;
    }

    private class LocationClientCallbacks implements LocationClient.LocationClientCallbacks {
        @Override
        public void onSuccess() {
            if (mapReady && !cameraIsMoving) {
                Location locationResult = locationClient.getLastLocation();

                if (new Date().getTime() - locationResult.getTime() > 60000) {
                    locationClient.updateLocation(new MapSearchFragment.LocationClientCallbacks());
                    return;
                }

                userLocation = locationResult;

                if (mapState == USER_HAS_NOT_MOVED_MAP && selectedStop == null) {
                    if (staleLocation) {
                        // If the user is far outside the MBTA's operating area, then center to Boston
                        if (userLocation.getLatitude() < 41.3 ||
                                userLocation.getLatitude() > 43.3 ||
                                userLocation.getLongitude() < -72.5 ||
                                userLocation.getLongitude() > -69.9) {
                            userLocation.setLatitude(42.3604);
                            userLocation.setLongitude(-71.0580);
                            mapTargetView.setVisibility(View.VISIBLE);
                            mapState = USER_HAS_MOVED_MAP;
                        }

                        gMap.moveCamera(CameraUpdateFactory.newLatLng(
                                new LatLng(userLocation.getLatitude(), userLocation.getLongitude())));
                        staleLocation = false;
                    } else {
                        gMap.animateCamera(CameraUpdateFactory.newLatLng(
                                new LatLng(userLocation.getLatitude(), userLocation.getLongitude())));
                    }

                    if (targetLocation.distanceTo(userLocation) >
                            DISTANCE_TO_FORCE_REFRESH) {
                        targetLocation = userLocation;
                        swipeRefreshLayout.setRefreshing(true);
                        forceUpdate();

                    } else if (!refreshing && targetLocation.distanceTo(userLocation) >
                            DISTANCE_TO_TARGET_LOCATION_UPDATE) {
                        targetLocation = userLocation;
                        backgroundUpdate();

                    } else {
                        backgroundUpdate();
                    }
                }
            }

            errorManager.setLocationError(false);
            errorManager.setLocationPermissionDenied(false);
        }

        @Override
        public void onError() {
            errorManager.setLocationError(true);

            backgroundUpdate();
        }

        @Override
        public void onNoPermission() {
            errorManager.setLocationPermissionDenied(true);

            backgroundUpdate();
        }
    }

    private class StopsPostExecuteListener implements StopsByLocationAsyncTask.OnPostExecuteListener {
        @Override
        public void onSuccess(Stop[] stops) {
            // Update the target stops
            targetStops.clear();
            for (Stop stop : stops) {
                targetStops.put(stop.getId(), stop);
            }

            // If there is no selected stop, then update the stop markers
            if (selectedStop == null) {
                refreshStopMarkers(stops);
            }

            // Update the target routes
            getRoutes();
        }

        @Override
        public void onError() {
            if (targetStops.size() > 0) {
                // If we have stops from a previous update, then proceed with current update
                getRoutes();

            } else {
                // If we have no stops, then show error message
                refreshing = false;
                enableOnErrorView(getResources().getString(R.string.error_stops));
                forceUpdate();
            }
        }
    }

    private class RoutesPostExecuteListener implements RoutesByStopsAsyncTask.OnPostExecuteListener {
        @Override
        public void onSuccess(Route[] routes) {
            targetRoutes.clear();

            for (Route route : routes) {
                targetRoutes.put(route.getId(), route);
            }

            getPredictions();
            getServiceAlerts();
        }

        @Override
        public void onError() {
            if (targetRoutes.size() > 0) {
                // If we have routes from a previous update, then proceed with current update
                getPredictions();
                getServiceAlerts();

            } else {
                // If we have no routes, then show error message
                refreshing = false;
                enableOnErrorView(getResources().getString(R.string.error_routes));
                forceUpdate();
            }
        }
    }

    private class PredictionsPostExecuteListener implements PredictionsAsyncTask.OnPostExecuteListener {
        @Override
        public void onSuccess(Prediction[] predictions, boolean live) {
            for (Prediction prediction : predictions) {
                // Replace prediction's stop ID with its parent stop ID
                if (targetStops.containsKey(prediction.getParentStopId())) {
                    prediction.setStop(targetStops.get(prediction.getParentStopId()));
                }

                // If the prediction is for the eastbound Green Line, then replace the route
                // with the Green Line Grouped route. This is to reduce the maximum number of
                // prediction cards displayed and reduces UI clutter.
                if (prediction.getRoute().getMode() == Route.LIGHT_RAIL &&
                        GreenLine.isGreenLineSubwayStop(prediction.getStopId())) {
                    if (targetRoutes.get(prediction.getRouteId()) != null &&
                            !targetRoutes.get(prediction.getRouteId()).hasPickUps(0) &&
                            !targetRoutes.get(prediction.getRouteId()).hasPickUps(1)) {
                        targetRoutes.remove(prediction.getRouteId());
                    }

                    prediction.setRoute(new GreenLineCombined());
                }

                // If the prediction is for the inbound Commuter Rail, then replace the route
                // with the Commuter Rail Grouped route. This is to reduce the maximum number
                // of prediction cards displayed and reduces UI clutter.
                if (prediction.getRoute().getMode() == Route.COMMUTER_RAIL &&
                        prediction.getDirection() == Direction.INBOUND &&
                        CommuterRail.isCommuterRailHub(prediction.getStopId(), false)) {

                    if (CommuterRailNorthSide.isNorthSideCommuterRail(prediction.getRoute().getId())) {
                        prediction.setRoute(new CommuterRailNorthSide());

                    } else if (CommuterRailSouthSide.isSouthSideCommuterRail(prediction.getRoute().getId())) {
                        prediction.setRoute(new CommuterRailSouthSide());

                    } else if (CommuterRailOldColony.isOldColonyCommuterRail(prediction.getRoute().getId())) {
                        prediction.setRoute(new CommuterRailOldColony());
                    }
                }

                // If the prediction is at Harvard destined for Harvard, then set to drop-off only
                if (prediction.getStop().getName().equals(prediction.getDestination()))
                    prediction.setPickUpType(Prediction.NO_PICK_UP);

                // Add route to routes list if not already there
                if (!targetRoutes.containsKey(prediction.getRouteId()))
                    targetRoutes.put(prediction.getRouteId(), prediction.getRoute());

                // Add stop to stops list if not already there
                if (!targetStops.containsKey(prediction.getStopId()))
                    targetStops.put(prediction.getStopId(), prediction.getStop());

                // Add route to its stop's routes list
                Stop targetStop = targetStops.get(prediction.getStopId());
                if (targetStop != null) {
                    targetStop.addRoute(prediction.getRoute());
                    prediction.setStop(targetStop);
                }

                // Add prediction to its respective route
                int direction = prediction.getDirection();
                String routeId = prediction.getRouteId();
                Stop stop = targetStops.get(prediction.getStopId());

                // If this prediction's stop is the route's nearest stop
                if (stop.equals(targetRoutes.get(routeId).getNearestStop(direction))) {
                    targetRoutes.get(routeId).addPrediction(prediction);

                    // If route does not have predictions in this prediction's direction
                } else if (!targetRoutes.get(routeId).hasPredictions(direction)) {
                    targetRoutes.get(routeId).setNearestStop(direction, stop);
                    targetRoutes.get(routeId).addPrediction(prediction);

                    // If this prediction's stop is closer than route's current nearest stop
                } else if (stop.getLocation().distanceTo(targetLocation) <
                        targetRoutes.get(routeId).getNearestStop(direction).getLocation()
                                .distanceTo(targetLocation)
                        && prediction.willPickUpPassengers()) {
                    targetRoutes.get(routeId).setNearestStop(direction, stop);
                    targetRoutes.get(routeId).addPrediction(prediction);
                }

                predictionsCount++;
            }

            if (live) {
                getSchedules();

            } else {
                refreshing = false;
                refreshPredictionViews();

                if (predictionsCount == 0)
                    forceUpdate();
            }
        }

        @Override
        public void onError() {
            refreshing = false;
            refreshPredictionViews();
            update();
        }
    }

    private class ServiceAlertsPostExecuteListener implements ServiceAlertsAsyncTask.OnPostExecuteListener {
        @Override
        public void onSuccess(ServiceAlert[] serviceAlerts) {
            for (ServiceAlert alert : serviceAlerts) {
                for (Route route : targetRoutes.values()) {
                    if (alert.affectsMode(route.getMode())) {
                        route.addServiceAlert(alert);
                    } else {
                        for (String affectedRouteId : alert.getAffectedRoutes()) {
                            if (route.equals(affectedRouteId)) {
                                route.addServiceAlert(alert);
                                break;
                            }
                        }
                    }
                }
            }
        }

        @Override
        public void onError() {
            getServiceAlerts();
        }
    }

    private class LocationUpdateTimerTask extends TimerTask {
        @Override
        public void run() {
            if (mapReady) {
                locationClient.updateLocation(new MapSearchFragment.LocationClientCallbacks());
            }
        }
    }

    private class PredictionsUpdateTimerTask extends TimerTask {
        @Override
        public void run() {
            backgroundUpdate();
        }
    }
}
