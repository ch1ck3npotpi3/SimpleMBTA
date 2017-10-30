package jackwtat.simplembta.fragments;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Date;

import jackwtat.simplembta.data.ServiceAlert;
import jackwtat.simplembta.data.Stop;
import jackwtat.simplembta.R;
import jackwtat.simplembta.QueryUtil;
import jackwtat.simplembta.StopDbHelper;
import jackwtat.simplembta.data.Trip;

/**
 * Created by jackw on 9/30/2017.
 */

public class NearbyListFragment extends PredictionsListFragment {
    private final String LOG_TAG = "NearbyListFragment";

    // Fine Location Permission
    private final int REQUEST_ACCESS_FINE_LOCATION = 1;

    // Time between location updates, in seconds
    private final long LOCATION_UPDATE_INTERVAL = 15;

    // Time since last refresh before predictions can automatically refresh onResume, in seconds
    private final long ON_RESUME_REFRESH_INTERVAL = 120;

    // Maximum distance to stop in miles
    private final double MAX_DISTANCE = .5;

    private LocationServicesClient locationServicesClient;
    private Location lastLocation;
    private PredictionAsyncTask predictionAsyncTask;
    private StopDbHelper stopDbHelper;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        locationServicesClient = new LocationServicesClient();
        stopDbHelper = new StopDbHelper(getContext());
    }

    @Override
    public void onStart() {
        super.onStart();

        // Get location access permission from user
        if (ActivityCompat.checkSelfPermission(getContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getActivity(),
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_ACCESS_FINE_LOCATION);

        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // If sufficient time has lapsed since last refresh, then automatically refreshed predictions
        Date currentTime = new Date();
        if (lastRefreshed == null ||
                ((currentTime.getTime() - lastRefreshed.getTime()) > 1000 * ON_RESUME_REFRESH_INTERVAL)) {
            refreshPredictions();
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        // Disconnect the LocationServicesClient
        locationServicesClient.disconnectClient();

        // Cancel the AsyncTask if it is running
        if (predictionAsyncTask != null && predictionAsyncTask.cancel(true)) {
            setRefreshProgress(0, "");
            setStatus(getResources().getString(R.string.refresh_canceled), "", false);
        }
    }

    @Override
    public void refreshPredictions() {
        setRefreshProgress(0, getResources().getString(R.string.getting_location));

        // Check if device is connected to the internet
        if (!checkNetworkConnection()) {
            clearList();
            setStatus(new Date(), getResources().getString(R.string.no_network_connectivity), false);
        } else {
            locationServicesClient.refreshLocation();
        }
    }

    private boolean checkNetworkConnection() {
        ConnectivityManager cm =
                (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    private void onLocationFound(boolean found) {
        if (!found) {
            clearList();
            setStatus(new Date(), getResources().getString(R.string.no_location), false);
        } else {
            predictionAsyncTask = new PredictionAsyncTask();
            predictionAsyncTask.execute(lastLocation);
        }
    }

    /*
     * Wrapper around the GoogleApiClient and LocationServices API
     * Allows for easy access to the device's current location and GPS coordinates
     */
    private class LocationServicesClient implements LocationListener, ConnectionCallbacks,
            OnConnectionFailedListener {
        private GoogleApiClient googleApiClient;
        private LocationRequest locationRequest;

        private LocationServicesClient() {
            googleApiClient = new GoogleApiClient.Builder(getContext())
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();

            locationRequest = LocationRequest.create();
            locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
            locationRequest.setInterval(1000 * LOCATION_UPDATE_INTERVAL);
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Log.i(LOG_TAG, "Location connection successful");
            getLocation();
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.i(LOG_TAG, "Location connection suspended");
        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            Log.e(LOG_TAG, "Location connection failed");
            onLocationFound(false);
        }

        @Override
        public void onLocationChanged(Location location) {
        }

        private void disconnectClient() {
            if (googleApiClient.isConnected()) {
                googleApiClient.disconnect();
            }
        }

        private void refreshLocation() {
            if (!googleApiClient.isConnected()) {
                googleApiClient.connect();
            } else {
                Log.i(LOG_TAG, "Google API client already connected");
                getLocation();
            }
        }

        private void getLocation() {
            if (ActivityCompat.checkSelfPermission(getContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.e(LOG_TAG, "Location permission missing");
                onLocationFound(false);
            } else {
                try {
                    LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient,
                            locationRequest, this);
                    lastLocation = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
                    onLocationFound(true);
                } catch (Exception ex) {
                    Log.e(LOG_TAG, "Location services error");
                    onLocationFound(false);
                }
            }
        }
    }

    /*
        AsyncTask that asynchronously queries the MBTA API and displays the results upon success
    */
    private class PredictionAsyncTask extends AsyncTask<Location, Integer, List<Stop>> {
        private final int LOADING_DATABASE = -1;
        private final int GETTING_NEARBY_STOPS = -2;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected List<Stop> doInBackground(Location... locations) {

            // Load the stops database
            publishProgress(LOADING_DATABASE);
            stopDbHelper.loadDatabase(getContext());

            // Get all stops within the specified maximum distance from user's location
            publishProgress(GETTING_NEARBY_STOPS);
            List<Stop> stops = stopDbHelper.getStopsByLocation(locations[0], MAX_DISTANCE);

            // Get all service alerts
            HashMap<String, ArrayList<ServiceAlert>> alerts = QueryUtil.fetchAlerts(getString(R.string.mbta_realtime_api_key));

            // Get predicted trips for each stop
            for (int i = 0; i < stops.size(); i++) {
                Stop stop = stops.get(i);

                stop.addTrips(QueryUtil.fetchPredictionsByStop(getString(R.string.mbta_realtime_api_key), stop.getId()));

                // Add alerts to trips whose route has alerts
                for (Trip trip : stop.getTrips()) {
                    if (alerts.containsKey(trip.getRouteId())) {
                        trip.setAlerts(alerts.get(trip.getRouteId()));
                    }
                }

                publishProgress((int) (100 * (i + 1) / stops.size()));
            }

            return stops;
        }

        protected void onProgressUpdate(Integer... progress) {
            if(progress[0] == LOADING_DATABASE){
                setRefreshProgress(0, getResources().getString(R.string.loading_database));
            }else if (progress[0] == GETTING_NEARBY_STOPS) {
                setRefreshProgress(0, getResources().getString(R.string.getting_nearby_stops));
            } else {
                setRefreshProgress(progress[0], getResources().getString(R.string.getting_predictions));
            }
        }

        @Override
        protected void onPostExecute(List<Stop> stops) {
            populateList(stops);
        }
    }
}