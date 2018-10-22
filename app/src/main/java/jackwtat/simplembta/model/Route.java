package jackwtat.simplembta.model;

import android.content.Context;
import android.support.annotation.NonNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import jackwtat.simplembta.R;

public class Route implements Comparable<Route>, Serializable {
    public static final int OUTBOUND = 0;
    public static final int INBOUND = 1;
    public static final int WESTBOUND = 0;
    public static final int EASTBOUND = 1;
    public static final int SOUTHBOUND = 0;
    public static final int NORTHBOUND = 1;
    public static final int NULL_DIRECTION = 0;

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

    private ArrayList<ServiceAlert> serviceAlerts = new ArrayList<>();

    private Stop nearestInboundStop = null;
    private Stop nearestOutboundStop = null;
    private ArrayList<Prediction> inboundPredictions = new ArrayList<>();
    private ArrayList<Prediction> outboundPredictions = new ArrayList<>();

    public Route(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public int getMode() {
        return mode;
    }

    public String getShortName() {
        return shortName;
    }

    public String getLongName() {
        return longName;
    }

    // Returns the language-specific short name of this route
    // Context is required to get the proper translation
    public String getShortDisplayName(Context context) {
        if (mode == HEAVY_RAIL) {
            if (id.equals("Red"))
                return context.getResources().getString(R.string.red_line_short_name);
            else if (id.equals("Orange"))
                return context.getResources().getString(R.string.orange_line_short_name);
            else if (id.equals("Blue"))
                return context.getResources().getString(R.string.blue_line_short_name);
            else
                return id;

        } else if (mode == LIGHT_RAIL) {
            if (id.equals("Green-B"))
                return context.getResources().getString(R.string.green_line_b_short_name);
            else if (id.equals("Green-C"))
                return context.getResources().getString(R.string.green_line_c_short_name);
            else if (id.equals("Green-D"))
                return context.getResources().getString(R.string.green_line_d_short_name);
            else if (id.equals("Green-E"))
                return context.getResources().getString(R.string.green_line_e_short_name);
            else if (id.equals("Mattapan"))
                return context.getResources().getString(R.string.red_line_mattapan_short_name);
            else
                return id;

        } else if (mode == BUS) {
            if (id.equals("746"))
                return context.getResources().getString(R.string.silver_line_waterfront_short_name);
            else if (!shortName.equals("") && !shortName.equals("null"))
                return shortName;
            else
                return id;

        } else if (mode == COMMUTER_RAIL) {
            if (id.equals("CapeFlyer")) {
                return context.getResources().getString(R.string.cape_flyer_short_name);
            } else {
                return context.getResources().getString(R.string.commuter_rail_short_name);
            }

        } else if (mode == FERRY) {
            return context.getResources().getString(R.string.ferry_short_name);

        } else {
            return id;
        }
    }

    // Returns the language-specific full name of this route
    // Context is required to get the proper translation
    public String getLongDisplayName(Context context) {
        if (mode == BUS) {
            if (longName.contains("Silver Line") || shortName.contains("SL")) {
                return context.getResources().getString((R.string.silver_line_long_name)) +
                        " " + shortName;
            } else {
                return context.getResources().getString(R.string.route_prefix) +
                        " " + shortName;
            }
        } else if (!longName.equals("") && !longName.equals("null")) {
            return longName;
        } else {
            return id;
        }
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

    public int getSortOrder() {
        return sortOrder;
    }

    public Stop getNearestStop(int direction) {
        if (direction == INBOUND) {
            return nearestInboundStop;
        } else if (direction == OUTBOUND) {
            return nearestOutboundStop;
        } else {
            return null;
        }
    }

    public ArrayList<Prediction> getPredictions(int direction) {
        if (direction == INBOUND) {
            return inboundPredictions;
        } else if (direction == OUTBOUND) {
            return outboundPredictions;
        } else {
            return new ArrayList<>();
        }
    }

    public ArrayList<ServiceAlert> getServiceAlerts() {
        return serviceAlerts;
    }

    public void setMode(int mode) {
        this.mode = mode;
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

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void setNearestStop(int direction, Stop stop, boolean clearPredictions) {
        if (direction == INBOUND) {
            if (clearPredictions) {
                inboundPredictions.clear();
            }

            if (nearestInboundStop == null || !nearestInboundStop.equals(stop)) {
                nearestInboundStop = stop;
            }
        } else if (direction == OUTBOUND) {
            if (clearPredictions) {
                outboundPredictions.clear();
            }

            if (nearestOutboundStop == null || !nearestOutboundStop.equals(stop)) {
                nearestOutboundStop = stop;
            }
        }
    }

    public void addPrediction(Prediction prediction) {
        if (prediction.getDestination().equals("Silver Line Way") && !id.equals("746")) {
            return;
        }

        if (prediction.getDirection() == INBOUND) {
            inboundPredictions.add(prediction);

        } else if (prediction.getDirection() == OUTBOUND) {
            outboundPredictions.add(prediction);
        }
    }

    public void addAllPredictions(List<Prediction> predictions) {
        for (Prediction p : predictions) {
            addPrediction(p);
        }
    }

    public void clearPredictions(int direction) {
        if (direction == INBOUND) {
            inboundPredictions.clear();
        } else if (direction == OUTBOUND) {
            outboundPredictions.clear();
        }
    }

    public boolean hasPredictions(int direction) {
        if (direction == INBOUND) {
            return inboundPredictions.size() > 0;
        } else if (direction == OUTBOUND) {
            return outboundPredictions.size() > 0;
        } else {
            return false;
        }
    }

    public boolean hasPickUps(int direction) {
        if (direction == INBOUND) {
            for (Prediction p : inboundPredictions) {
                if (p.willPickUpPassengers()) {
                    return true;
                }
            }
            return false;
        } else if (direction == OUTBOUND) {
            for (Prediction p : outboundPredictions) {
                if (p.willPickUpPassengers()) {
                    return true;
                }
            }
            return false;
        } else {
            return false;
        }
    }

    public boolean hasLivePickUps(int direction) {
        if (direction == INBOUND) {
            for (Prediction p : inboundPredictions) {
                if (p.willPickUpPassengers() && p.isLive()) {
                    return true;
                }
            }
            return false;
        } else if (direction == OUTBOUND) {
            for (Prediction p : outboundPredictions) {
                if (p.willPickUpPassengers() && p.isLive()) {
                    return true;
                }
            }
            return false;
        } else {
            return false;
        }
    }

    public boolean hasNearbyStops() {
        return nearestInboundStop != null || nearestOutboundStop != null;
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

    public boolean isParentOf(String otherRouteId) {
        if (id.equals("2427") && (otherRouteId.equals("24") || otherRouteId.equals("27")))
            return true;
        else if (id.equals("3233") && (otherRouteId.equals("32") || otherRouteId.equals("33")))
            return true;
        else if (id.equals("3738") && (otherRouteId.equals("37") || otherRouteId.equals("38")))
            return true;
        else if (id.equals("4050") && (otherRouteId.equals("40") || otherRouteId.equals("50")))
            return true;
        else if (id.equals("627") && (otherRouteId.equals("62") || otherRouteId.equals("76")))
            return true;
        else if (id.equals("725") && (otherRouteId.equals("72") || otherRouteId.equals("75")))
            return true;
        else if (id.equals("8993") && (otherRouteId.equals("89") || otherRouteId.equals("93")))
            return true;
        else if (id.equals("116117") && (otherRouteId.equals("116") || otherRouteId.equals("117")))
            return true;
        else if (id.equals("214216") && (otherRouteId.equals("214") || otherRouteId.equals("216")))
            return true;
        else if (id.equals("441442") && (otherRouteId.equals("441") || otherRouteId.equals("442")))
            return true;
        else return false;
    }

    public boolean isSilverLine() {
        return id.equals("741") ||
                id.equals("742") ||
                id.equals("743") ||
                id.equals("746") ||
                id.equals("749") ||
                id.equals("751");
    }

    public boolean isGreenLine() {
        return id.equals("Green-B") ||
                id.equals("Green-C") ||
                id.equals("Green-D") ||
                id.equals("Green-E") ||
                id.equals("Green-B,Green-C,Green-D,Green-E");
    }

    public boolean isNorthSideCommuterRail() {
        return id.equals("CR-Fitchburg") ||
                id.equals("CR-Haverhill") ||
                id.equals("CR-Lowell") ||
                id.equals("CR-Newburyport");
    }

    public boolean isSouthSideCommuterRail() {
        return id.equals("CR-Fairmount") ||
                id.equals("CR-Worcester") ||
                id.equals("CR-Franklin") ||
                id.equals("CR-Needham") ||
                id.equals("CR-Providence") ||
                id.equals("CR-Foxboro");
    }

    public boolean isOldColonyCommuterRail() {
        return id.equals("CR-Greenbush") ||
                id.equals("CR-Kingston") ||
                id.equals("CR-Middleborough");
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
        } else {
            return false;
        }
    }

    public boolean idEquals(String id) {
        return this.id.equals(id);
    }

    public static class GreenLineGroup extends Route {
        String[] ids = {"Green-B", "Green-C", "Green-D", "Green-E"};

        public GreenLineGroup() {
            super("Green-B,Green-C,Green-D,Green-E");
            setMode(LIGHT_RAIL);
            setShortName("GL");
            setLongName("Green Line");
            setPrimaryColor("00843D");
            setTextColor("FFFFFF");
            setSortOrder(4);
        }

        @Override
        public boolean idEquals(String routeId) {
            for (String id : ids) {
                if (id.equals(routeId)) {
                    return true;
                }
            }

            return false;
        }
    }

    public static class NorthSideCommuterRail extends Route {
        String[] ids = {"CR-Fitchburg", "CR-Haverhill", "CR-Lowell", "CR-Newburyport"};

        public NorthSideCommuterRail() {
            super("CR-Fitchburg,CR-Haverhill,CR-Lowell,CR-Newburyport");
            setMode(COMMUTER_RAIL);
            setShortName("CR");
            setLongName("Commuter Rail");
            setPrimaryColor("80276C");
            setTextColor("FFFFFF");
            setSortOrder(50);
        }

        @Override
        public boolean idEquals(String routeId) {
            for (String id : ids) {
                if (id.equals(routeId)) {
                    return true;
                }
            }

            return false;
        }
    }

    public static class SouthSideCommuterRail extends Route {
        String[] ids = {"CR-Fairmount", "CR-Worcester", "CR-Franklin", "CR-Needham",
                "CR-Providence", "CR-Foxboro"};

        public SouthSideCommuterRail() {
            super("CR-Fairmount,CR-Worcester,CR-Franklin,CR-Needham,CR-Providence,CR-Foxboro");
            setMode(COMMUTER_RAIL);
            setShortName("CR");
            setLongName("Commuter Rail");
            setPrimaryColor("80276C");
            setTextColor("FFFFFF");
            setSortOrder(50);
        }

        @Override
        public boolean idEquals(String routeId) {
            for (String id : ids) {
                if (id.equals(routeId)) {
                    return true;
                }
            }

            return false;
        }
    }

    public static class OldColonyCommuterRail extends Route {
        String[] ids = {"CR-Greenbush", "CR-Kingston", "CR-Middleborough"};

        public OldColonyCommuterRail() {
            super("CR-Greenbush,CR-Kingston,CR-Middleborough");
            setMode(COMMUTER_RAIL);
            setShortName("CR");
            setLongName("Commuter Rail");
            setPrimaryColor("80276C");
            setTextColor("FFFFFF");
            setSortOrder(50);
        }

        @Override
        public boolean idEquals(String routeId) {
            for (String id : ids) {
                if (id.equals(routeId)) {
                    return true;
                }
            }

            return false;
        }
    }
}
