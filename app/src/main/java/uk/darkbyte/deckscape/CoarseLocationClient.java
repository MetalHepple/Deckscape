package uk.darkbyte.deckscape;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

/** Requests one foreground-only coarse location fix and stops immediately after delivery. */
final class CoarseLocationClient {
    /** Receives a single location or an actionable failure message. */
    interface Callback {
        void onLocation(Location location);

        void onError(String message);
    }

    private static final long TIMEOUT_MS = 15_000L;
    private static final long ACCEPT_LAST_KNOWN_AGE_MS = 30L * 24 * 60 * 60 * 1000;

    private final Context context;
    private final LocationManager manager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LocationListener listener;
    private Runnable timeout;

    CoarseLocationClient(Context context) {
        this.context = context.getApplicationContext();
        manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    @SuppressWarnings("MissingPermission") // Permission is checked before provider access.
    void request(Callback callback) {
        cancel();
        if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            callback.onError("Approximate location permission is not enabled");
            return;
        }
        if (manager == null) {
            callback.onError("This head unit has no Android location service");
            return;
        }

        List<String> providers = enabledProviders();
        Location best = bestLastKnown(providers);
        if (best != null && System.currentTimeMillis() - best.getTime()
                <= ACCEPT_LAST_KNOWN_AGE_MS) {
            callback.onLocation(best);
            return;
        }
        if (providers.isEmpty()) {
            if (best != null) callback.onLocation(best);
            else callback.onError("No location provider is available; use manual times");
            return;
        }

        Location fallback = best;
        listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                cancel();
                callback.onLocation(location);
            }

            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}

            @Override public void onProviderEnabled(String provider) {}

            @Override public void onProviderDisabled(String provider) {}
        };
        boolean registered = false;
        for (String provider : providers) {
            try {
                manager.requestLocationUpdates(provider, 0, 0, listener,
                        Looper.getMainLooper());
                registered = true;
            } catch (RuntimeException ignored) {
                // Try the next enabled provider.
            }
        }
        if (!registered) {
            cancel();
            if (fallback != null) callback.onLocation(fallback);
            else callback.onError("Unable to request an approximate location; use manual times");
            return;
        }
        timeout = () -> {
            cancel();
            if (fallback != null) callback.onLocation(fallback);
            else callback.onError("Location timed out; use manual times or try again outdoors");
        };
        handler.postDelayed(timeout, TIMEOUT_MS);
    }

    void cancel() {
        if (timeout != null) handler.removeCallbacks(timeout);
        timeout = null;
        if (manager != null && listener != null) {
            try {
                manager.removeUpdates(listener);
            } catch (RuntimeException ignored) {
                // The provider may already have removed a one-shot listener.
            }
        }
        listener = null;
    }

    private List<String> enabledProviders() {
        List<String> result = new ArrayList<>();
        for (String provider : new String[]{LocationManager.NETWORK_PROVIDER,
                LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER}) {
            try {
                if (manager.isProviderEnabled(provider)) result.add(provider);
            } catch (RuntimeException ignored) {
                // Vendor images may omit a standard provider.
            }
        }
        return result;
    }

    @SuppressWarnings("MissingPermission")
    private Location bestLastKnown(List<String> providers) {
        Location best = null;
        List<String> candidates = new ArrayList<>(providers);
        for (String provider : new String[]{LocationManager.NETWORK_PROVIDER,
                LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER}) {
            if (!candidates.contains(provider)) candidates.add(provider);
        }
        for (String provider : candidates) {
            try {
                Location value = manager.getLastKnownLocation(provider);
                if (value != null && (best == null || value.getTime() > best.getTime())) {
                    best = value;
                }
            } catch (RuntimeException ignored) {
                // Missing or disabled providers are normal on automotive Android builds.
            }
        }
        return best;
    }
}
