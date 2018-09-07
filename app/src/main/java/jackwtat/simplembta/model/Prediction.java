package jackwtat.simplembta.model;

import android.support.annotation.NonNull;

import java.util.Date;

public class Prediction implements Comparable<Prediction> {
    // Prediction data
    private String id;
    private String trackNumber = "null";
    private Date arrivalTime = null;
    private Date departureTime = null;

    // Route data
    private String routeId = "null";

    // Stop data
    private String stopId = "null";

    // Trip data
    private String tripId = "null";
    private int direction = 0;
    private String destination = "null";
    private String tripName = "null";

    public Prediction(String id) {
        this.id = id;
    }

    // Prediction data getters
    public String getId() {
        return id;
    }

    public String getTrackNumber() {
        return trackNumber;
    }

    public Date getArrivalTime() {
        return arrivalTime;
    }

    public Date getDepartureTime() {
        return departureTime;
    }

    public long getTimeUntilArrival() {
        if (arrivalTime != null) {
            return arrivalTime.getTime() - new Date().getTime();
        } else {
            return -1;
        }
    }

    public long getTimeUntilDeparture() {
        if (departureTime != null) {
            return departureTime.getTime() - new Date().getTime();
        } else {
            return -1;
        }
    }

    public String getRouteId() {
        return routeId;
    }

    // Stop data getters
    public String getStopId() {
        return stopId;
    }

    // Trip data getters
    public String getTripId() {
        return tripId;
    }

    public int getDirection() {
        return direction;
    }

    public String getDestination() {
        return destination;
    }

    public String getTripName() {
        return tripName;
    }

    // Prediction data setters
    public void setTrackNumber(String trackNumber) {
        this.trackNumber = trackNumber;
    }

    public void setArrivalTime(Date arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public void setDepartureTime(Date departureTime) {
        this.departureTime = departureTime;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    // Stop data setters
    public void setStopId(String stopId) {
        this.stopId = stopId;
    }

    // Trip data setters
    public void setTripId(String tripId) {
        this.tripId = tripId;
    }

    public void setDirection(int direction) {
        this.direction = direction;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public void setTripName(String tripName) {
        this.tripName = tripName;
    }

    @Override
    public int compareTo(@NonNull Prediction otherPred) {
        Date otherDeparture = otherPred.getDepartureTime();
        if (departureTime == null && otherDeparture == null) {
            return 0;
        } else if (departureTime == null) {
            return 1;
        } else if (otherDeparture == null) {
            return -1;
        } else {
            return this.departureTime.compareTo(otherDeparture);
        }
    }
}
