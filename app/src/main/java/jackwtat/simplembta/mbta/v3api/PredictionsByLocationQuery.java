package jackwtat.simplembta.mbta.v3api;

import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import jackwtat.simplembta.mbta.structure.Prediction;
import jackwtat.simplembta.mbta.structure.ServiceAlert;
import jackwtat.simplembta.mbta.structure.Mode;
import jackwtat.simplembta.mbta.structure.Route;
import jackwtat.simplembta.mbta.structure.Stop;
import jackwtat.simplembta.mbta.structure.Trip;

/**
 * Created by jackw on 1/18/2018.
 */

public class PredictionsByLocationQuery extends Query {
    private static final String LOG_TAG = "PredByLocationQuery";

    public PredictionsByLocationQuery(String apiKey) {
        super(apiKey);
    }

    public HashMap<String, Stop> get(double latitude, double longitude) {
        HashMap<String, Stop> stops = new HashMap<>();
        HashMap<String, Route> routes = new HashMap<>();
        HashMap<String, Trip> trips = new HashMap<>();
        ArrayList<ServiceAlert> alerts = new ArrayList<>();
        ArrayList<String> parentStopIds = new ArrayList<>();

        String[] params = {
                "filter[latitude]=" + Double.toString(latitude),
                "filter[longitude]=" + Double.toString(longitude),
                "include=stop,route,trip"
        };

        String jsonResponse = super.get("predictions", params);

        if (TextUtils.isEmpty(jsonResponse)) {
            return stops;
        }

        try {
            JSONObject jRoot = new JSONObject(jsonResponse);

            // Parse linked data (Stop, Route, and Trip)
            if (jRoot.has("included")) {
                JSONArray jLinked = jRoot.getJSONArray("included");

                // Loop through all the linked objects
                for (int i = 0; i < jLinked.length(); i++) {
                    JSONObject jLinkedObj = jLinked.getJSONObject(i);

                    // Parse linked object
                    try {
                        String id = jLinkedObj.getString("id");
                        String type = jLinkedObj.getString("type");
                        JSONObject jAttributes = jLinkedObj.getJSONObject("attributes");

                        if (type.equals("stop")) {
                            String name = jAttributes.getString("name");
                            double stopLat = jAttributes.getDouble("latitude");
                            double stopLon = jAttributes.getDouble("longitude");

                            try {
                                String parentStopId = jLinkedObj
                                        .getJSONObject("relationships")
                                        .getJSONObject("parent_station")
                                        .getJSONObject("data")
                                        .getString("id");

                                parentStopIds.add(parentStopId);

                                stops.put(id, new Stop(
                                        id, name, parentStopId, stopLat, stopLon,
                                        latitude, longitude));

                            } catch (JSONException e) {
                                stops.put(id, new Stop(
                                        id, name, stopLat, stopLon, latitude, longitude));
                            }

                        } else if (type.equals("route")) {
                            Mode mode = Mode.getModeFromType(jAttributes.getInt("type"));
                            String shortName = jAttributes.getString("short_name");
                            String longName = jAttributes.getString("long_name");
                            String color = "#" + jAttributes.getString("color");
                            String textColor = "#" + jAttributes.getString("text_color");
                            int sortOrder = jAttributes.getInt("sort_order");

                            routes.put(id, new Route(
                                    id, mode, shortName, longName, color, textColor, sortOrder));

                        } else if (type.equals("trip")) {
                            int direction = jAttributes.getInt("direction_id");
                            String destination = jAttributes.getString("headsign");
                            String name = jAttributes.getString("name");

                            trips.put(id, new Trip(id, direction, destination, name));

                        } else {
                            Log.e(LOG_TAG, "Unknown linked object type:\n" +
                                    jLinkedObj.toString());
                        }

                    } catch (JSONException e) {
                        Log.e(LOG_TAG, "Unable to parse linked object:\n" +
                                jLinkedObj.toString());
                        e.printStackTrace();
                    }
                }
            } else {
                Log.i(LOG_TAG, "No linked Stop, Route, or Trip data returned");
            }

            // Add service alerts to their respective routes
            alerts.addAll(new AlertsByRoutesQuery(apiKey).get(new ArrayList<>(routes.keySet())));
            for (ServiceAlert alert : alerts) {
                for (Route route : routes.values()) {
                    if (alert.getAffectedRoutes().contains(route.getId()) ||
                            alert.getBlanketModes().contains(route.getMode())) {
                        route.addServiceAlert(alert);
                    }
                }
            }

            // Get Parent Stops data
            if (parentStopIds.size() > 0) {
                HashMap<String, Stop> parentStops = new StopsByIdQuery(apiKey).get(parentStopIds);

                for (Stop s : parentStops.values()) {
                    s.setDistance(latitude, longitude);
                }

                stops.putAll(parentStops);
            }

            // Parse MbtaPrediction data
            if (jRoot.has("data")) {
                JSONArray jData = jRoot.getJSONArray("data");

                // Loop through all Predictions
                for (int i = 0; i < jData.length(); i++) {
                    JSONObject jPrediction = jData.getJSONObject(i);

                    // Parse MbtaPrediction object
                    try {
                        String id = jPrediction.getString("id");

                        JSONObject jAttributes = jPrediction.getJSONObject("attributes");

                        String trackNumber = jAttributes.getString("track");
                        String departure = jAttributes.getString("departure_time");
                        String arrival = jAttributes.getString("arrival_time");

                        if (!departure.equals("null") ||
                                (departure.equals("null") && arrival.equals("null"))) {

                            JSONObject jRelatedData = jPrediction.getJSONObject("relationships");

                            Stop relatedStop = stops.get(jRelatedData
                                    .getJSONObject("stop")
                                    .getJSONObject("data")
                                    .getString("id"));

                            Route relatedRoute = routes.get(jRelatedData
                                    .getJSONObject("route")
                                    .getJSONObject("data")
                                    .getString("id"));

                            Trip relatedTrip = trips.get(jRelatedData
                                    .getJSONObject("trip")
                                    .getJSONObject("data")
                                    .getString("id"));

                            if (relatedStop != null && relatedRoute != null && relatedTrip != null &&
                                    relatedRoute.isValidDestination(relatedTrip.getDestination())) {
                                Date deptTime = Query.parseDate(departure);

                                if (relatedStop.hasParentStop()) {
                                    Stop parentStop = stops.get(relatedStop.getParentStopId());
                                    stops.get(parentStop.getId())
                                            .addPrediction(new Prediction(
                                                    id,
                                                    parentStop.getId(),
                                                    parentStop.getName(),
                                                    trackNumber,
                                                    relatedRoute,
                                                    relatedTrip,
                                                    deptTime));
                                } else {
                                    relatedStop.addPrediction(new Prediction(
                                            id,
                                            relatedStop.getId(),
                                            relatedStop.getName(),
                                            trackNumber,
                                            relatedRoute,
                                            relatedTrip,
                                            deptTime));
                                }
                            }
                        }
                    } catch (JSONException e) {
                        Log.e(LOG_TAG, "Unable to parse MbtaPrediction:\n" +
                                jPrediction.toString());
                    }
                }
            } else {
                Log.i(LOG_TAG, "No MbtaPrediction data returned");
            }

        } catch (JSONException e) {
            Log.e(LOG_TAG, "Unable to parse JSON response for Predictions");
            e.printStackTrace();
        }

        return stops;
    }
}
