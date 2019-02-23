package jackwtat.simplembta.asyncTasks;

import android.os.AsyncTask;

import jackwtat.simplembta.clients.RealTimeApiClient;
import jackwtat.simplembta.model.ServiceAlert;
import jackwtat.simplembta.jsonParsers.ServiceAlertsJsonParser;

public class ServiceAlertsAsyncTask extends AsyncTask<Void, Void, ServiceAlert[]> {
    private String realTimeApiKey;
    private String[] routeIds;
    private OnPostExecuteListener onPostExecuteListener;

    public ServiceAlertsAsyncTask(String realTimeApiKey,
                                  String[] routeIds,
                                  OnPostExecuteListener onPostExecuteListener) {
        this.realTimeApiKey = realTimeApiKey;
        this.routeIds = routeIds;
        this.onPostExecuteListener = onPostExecuteListener;
    }

    @Override
    protected ServiceAlert[] doInBackground(Void... voids) {
        if (routeIds.length == 0) {
            return new ServiceAlert[0];
        }

        RealTimeApiClient realTimeApiClient = new RealTimeApiClient(realTimeApiKey);

        StringBuilder routeArgBuilder = new StringBuilder();

        for (String routeId : routeIds) {
            routeArgBuilder.append(routeId).append(",");
        }

        String[] routeArgs = {
                "filter[route]=" + routeArgBuilder.toString(),
                "include=stops"
        };

        String jsonResponse = realTimeApiClient.get("alerts", routeArgs);

        if (jsonResponse != null)
            return ServiceAlertsJsonParser.parse(jsonResponse);
        else {
            return null;
        }
    }

    @Override
    protected void onPostExecute(ServiceAlert[] serviceAlerts) {
        if (serviceAlerts != null)
            onPostExecuteListener.onSuccess(serviceAlerts);
        else
            onPostExecuteListener.onError();
    }

    public interface OnPostExecuteListener {
        void onSuccess(ServiceAlert[] serviceAlerts);

        void onError();
    }
}
