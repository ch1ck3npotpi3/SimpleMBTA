package jackwtat.simplembta.model.routes;

import jackwtat.simplembta.model.Stop;

public class RailRoute extends Route {
    public RailRoute(String id) {
        super(id);
    }

    @Override
    public Stop getNearestStop(int directionId) {
        if (super.getNearestStop(directionId) == null)
            setNearestStop(directionId, super.getNearestStop((directionId + 1) % 2), true);

        return super.getNearestStop(directionId);
    }
}
