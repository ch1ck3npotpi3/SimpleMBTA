package jackwtat.simplembta.model.routes;

import jackwtat.simplembta.map.RedLineStopMarkerFactory;

public class RedLine extends RailRoute {
    public RedLine(String id) {
        super(id);
        setPrimaryColor("da291c");
        setTextColor("FFFFFF");
        setStopMarkerFactory(new RedLineStopMarkerFactory());
    }

    public static boolean isRedLine(String id) {
        return id.equals("Red") || id.equals("Mattapan");
    }

    public static boolean isMattapanLine(String id) {
        return id.equals("Mattapan");
    }
}
