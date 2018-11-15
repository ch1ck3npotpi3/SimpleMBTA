package jackwtat.simplembta.model.routes;

import android.support.annotation.NonNull;

import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import jackwtat.simplembta.map.markers.StopMarkerFactory;
import jackwtat.simplembta.model.Direction;
import jackwtat.simplembta.model.Prediction;
import jackwtat.simplembta.model.ServiceAlert;
import jackwtat.simplembta.model.Shape;
import jackwtat.simplembta.model.Stop;
import jackwtat.simplembta.model.Vehicle;

public class Route implements Comparable<Route>, Serializable {
    public static final int LIGHT_RAIL = 0;
    public static final int HEAVY_RAIL = 1;
    public static final int COMMUTER_RAIL = 2;
    public static final int BUS = 3;
    public static final int FERRY = 4;
    public static final int UNKNOWN_MODE = -1;

    private String id;
    private int mode = UNKNOWN_MODE;
    private int sortOrder = -1;
    private String shortName = "null";
    private String longName = "null";
    private String primaryColor = "#FFFFFF";
    private String accentColor = "#3191E1";
    private String textColor = "#000000";
    private Direction[] directions = {
            new Direction(Direction.OUTBOUND, "Outbound"),
            new Direction(Direction.INBOUND, "Inbound")};
    private Shape[] shapes = {};
    private StopMarkerFactory markerFactory = new StopMarkerFactory();
    private Vehicle[] vehicles = {};
    private ArrayList<ServiceAlert> serviceAlerts = new ArrayList<>();

    private Stop[] nearestStops = new Stop[2];
    private ArrayList<ArrayList<Prediction>> predictions = new ArrayList<>();

    public Route(String id) {
        this.id = id;

        for (int i = 0; i < 2; i++) {
            predictions.add(new ArrayList<Prediction>());
        }
    }

    public String getId() {
        return id;
    }

    public int getMode() {
        return mode;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public String getShortName() {
        return shortName;
    }

    public String getLongName() {
        return longName;
    }

    public String getPrimaryColor() {
        return primaryColor;
    }

    public String getAccentColor() {
        return accentColor;
    }

    public String getTextColor() {
        return textColor;
    }


    public Direction[] getAllDirections() {
        return directions;
    }

    public Direction getDirection(int directionId) {
        if (directionId == 0 || directionId == 1)
            return directions[directionId];
        else
            return new Direction(directionId, "null");
    }

    public Shape[] getAllShapes() {
        return shapes;
    }

    public Shape[] getShapes(int directionId) {
        ArrayList<Shape> directionalShapes = new ArrayList<>();

        for (Shape s : shapes) {
            if (s.getDirection() == directionId) {
                directionalShapes.add(s);
            }
        }

        return directionalShapes.toArray(new Shape[0]);
    }

    public MarkerOptions getStopMarkerOptions() {
        return markerFactory.createMarkerOptions();
    }

    public BitmapDescriptor getStopMarkerIcon() {
        return markerFactory.getIcon();
    }

    public Vehicle[] getAllVehicles() {
        return vehicles;
    }

    public Vehicle[] getVehicles(int directionId) {
        ArrayList<Vehicle> directionalVehicles = new ArrayList<>();

        for (Vehicle v : vehicles) {
            if (v.getDirection() == directionId) {
                directionalVehicles.add(v);
            }
        }

        return directionalVehicles.toArray(new Vehicle[0]);
    }

    public ArrayList<ServiceAlert> getServiceAlerts() {
        return serviceAlerts;
    }

    public Stop[] getAllStops() {
        ArrayList<Stop> sortedStops = new ArrayList<>();
        HashMap<String, Stop> addedStops = new HashMap<>();

        Shape[] allShapes = shapes;
        Arrays.sort(allShapes);

        for (Shape shape : allShapes) {
            if (shape.getPriority() >= 0) {
                for (Stop stop : shape.getStops()) {
                    if (!addedStops.containsKey(stop.getId())) {
                        sortedStops.add(stop);
                        addedStops.put(stop.getId(), stop);
                    }
                }
            }
        }

        return sortedStops.toArray(new Stop[0]);
    }

    public Stop[] getStops(int directionId) {
        ArrayList<Stop> sortedStops = new ArrayList<>();
        HashMap<String, Stop> addedStops = new HashMap<>();

        Shape[] directionalShapes = getShapes(directionId);
        Arrays.sort(directionalShapes);

        for (Shape shape : directionalShapes) {
            if (shape.getPriority() >= 0) {
                for (Stop stop : shape.getStops()) {
                    if (!addedStops.containsKey(stop.getId())) {
                        sortedStops.add(stop);
                        addedStops.put(stop.getId(), stop);
                    }
                }
            }
        }

        return sortedStops.toArray(new Stop[0]);
    }

    public Stop getNearestStop(int directionId) {
        if (directionId == 0 || directionId == 1) {
            return nearestStops[directionId];
        } else {
            return null;
        }
    }

    public ArrayList<Prediction> getPredictions(int directionId) {
        if (directionId == 0 || directionId == 1) {
            return predictions.get(directionId);
        } else {
            return null;
        }
    }

    public void setMode(int mode) {
        this.mode = mode;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void setShortName(String shortRouteName) {
        this.shortName = shortRouteName;
    }

    public void setLongName(String longRouteName) {
        this.longName = longRouteName;
    }

    public void setPrimaryColor(String primaryColor) {
        if (primaryColor.startsWith("#")) {
            this.primaryColor = primaryColor;
        } else {
            this.primaryColor = "#" + primaryColor;
        }
    }

    public void setAccentColor(String accentColor) {
        if (accentColor.startsWith("#")) {
            this.accentColor = accentColor;
        } else {
            this.accentColor = "#" + accentColor;
        }
    }

    public void setTextColor(String textColor) {
        if (textColor.startsWith("#")) {
            this.textColor = textColor;
        } else {
            this.textColor = "#" + textColor;
        }
    }

    public void setDirection(Direction direction) {
        if (direction.getId() == 0 || direction.getId() == 1)
            directions[direction.getId()] = direction;
    }

    public void setShapes(Shape[] shapes) {
        this.shapes = shapes;
    }

    public void setStopMarkerFactory(StopMarkerFactory factory) {
        this.markerFactory = factory;
    }

    public void setVehicles(Vehicle[] vehicles) {
        this.vehicles = vehicles;
    }

    public void addServiceAlert(ServiceAlert serviceAlert) {
        if (!serviceAlerts.contains(serviceAlert)) {
            serviceAlerts.add(serviceAlert);
        }
    }

    public void addAllServiceAlerts(ServiceAlert[] serviceAlerts) {
        this.serviceAlerts.addAll(Arrays.asList(serviceAlerts));
    }

    public boolean hasUrgentServiceAlerts() {
        for (ServiceAlert serviceAlert : serviceAlerts) {
            if (serviceAlert.isActive() &&
                    (serviceAlert.getLifecycle() == ServiceAlert.Lifecycle.NEW ||
                            serviceAlert.getLifecycle() == ServiceAlert.Lifecycle.UNKNOWN)) {
                return true;
            }
        }

        return false;
    }

    public void clearServiceAlerts() {
        serviceAlerts.clear();
    }

    public void setNearestStop(int direction, Stop stop) {
        if (direction == 0 || direction == 1) {
            predictions.get(direction).clear();
            nearestStops[direction] = stop;
        }
    }

    public void addPrediction(Prediction prediction) {
        int directionId = prediction.getDirection();

        if (directionId == 0 || directionId == 1) {
            predictions.get(directionId).add(prediction);
        }
    }

    public void addAllPredictions(List<Prediction> predictions) {
        for (Prediction p : predictions) {
            addPrediction(p);
        }
    }

    public void clearPredictions(int directionId) {
        if (directionId == 0 || directionId == 1) {
            predictions.get(directionId).clear();
        }
    }

    public boolean hasPredictions(int directionId) {
        if (directionId == 0 || directionId == 1) {
            return predictions.get(directionId).size() > 0;
        } else {
            return false;
        }
    }

    public boolean hasPickUps(int directionId) {
        if (directionId == 0 || directionId == 1) {
            for (Prediction p : predictions.get(directionId)) {
                if (p.willPickUpPassengers()) {
                    return true;
                }
            }
            return false;
        } else {
            return false;
        }
    }

    public boolean hasNearbyStops() {
        return nearestStops[0] != null || nearestStops[1] != null;
    }

    @Override
    public int compareTo(@NonNull Route otherRoute) {
        if (this.sortOrder != otherRoute.sortOrder) {
            return this.sortOrder - otherRoute.sortOrder;
        } else {
            return this.longName.compareTo(otherRoute.longName);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Route) {
            Route otherRoute = (Route) obj;
            return id.equals(otherRoute.id);

        } else if (obj instanceof String) {
            return id.equals(obj);

        } else {
            return false;
        }
    }
}
