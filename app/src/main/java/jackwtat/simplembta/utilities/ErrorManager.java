package jackwtat.simplembta.utilities;

import java.util.ArrayList;

public class ErrorManager {
    private static ErrorManager errorManager;

    private static ArrayList<OnErrorChangedListener> onErrorChangedListeners = new ArrayList<>();
    private static boolean networkError = false;
    private static boolean locationError = false;
    private static boolean locationPermissionDenied = false;
    private static boolean timeZoneMismatch = false;

    private ErrorManager() {
    }

    public static synchronized ErrorManager getErrorManager() {
        if (errorManager == null) {
            errorManager = new ErrorManager();
        }
        return errorManager;
    }

    public void setNetworkError(boolean error) {
        if (error != networkError) {
            networkError = error;
            notifyErrorChanged();
        }
    }

    public void setLocationError(boolean error) {
        if (error != locationError) {
            locationError = error;
            notifyErrorChanged();
        }
    }

    public void setLocationPermissionDenied(boolean error) {
        if (error != locationPermissionDenied) {
            locationPermissionDenied = error;
            notifyErrorChanged();
        }
    }

    public void setTimeZoneMismatch(boolean mismatch) {
        if (mismatch != timeZoneMismatch) {
            timeZoneMismatch = mismatch;
            notifyErrorChanged();
        }
    }

    public boolean hasNetworkError() {
        return networkError;
    }

    public boolean hasLocationError() {
        return locationError;
    }

    public boolean hasLocationPermissionDenied() {
        return locationPermissionDenied;
    }

    public boolean hasTimeZoneMismatch() {
        return timeZoneMismatch;
    }

    public void registerOnErrorChangeListener(OnErrorChangedListener listener) {
        onErrorChangedListeners.add(listener);
        listener.onErrorChanged();
    }

    private void notifyErrorChanged() {
        for (OnErrorChangedListener listener : onErrorChangedListeners) {
            try {
                listener.onErrorChanged();
            } catch (NullPointerException e) {

            }
        }
    }

    public interface OnErrorChangedListener {
        void onErrorChanged();
    }
}
