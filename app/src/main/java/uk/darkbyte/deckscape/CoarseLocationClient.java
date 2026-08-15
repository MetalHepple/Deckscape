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

/** Requests one foreground-only location fix and stops immediately after delivery. */
final class CoarseLocationClient {
    /** Receives a single location or an actionable failure message. */
    interface Callback {
        void onLocation(Location location, boolean cached);

        void onError(String message);
    }

    private static final String FUSED_PROVIDER = "fused";

    private final Context context;
    private final LocationManager manager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LocationListener listener;
    private Runnable timeout;
    private boolean requesting;

    CoarseLocationClient(Context context) {
        this.context = context.getApplicationContext();
        manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    @SuppressWarnings("MissingPermission") // Permission is checked before provider access.
    void request(Callback callback) {
        cancel();
        boolean coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (!coarse && !fine) {
            callback.onError("Location permission is not enabled");
            return;
        }
        if (manager == null) {
            callback.onError("Location is unavailable on this device");
            return;
        }

        List<String> providers = enabledProviders();
        Location best = bestLastKnown(providers);
        long now = System.currentTimeMillis();
        if (best != null && LocationFixPolicy.isRecent(now, best.getTime(),
                LocationFixPolicy.IMMEDIATE_CACHE_AGE_MS)) {
            callback.onLocation(best, true);
            return;
        }
        Location fallback = best != null && LocationFixPolicy.isRecent(now, best.getTime(),
                LocationFixPolicy.FALLBACK_CACHE_AGE_MS) ? best : null;
        if (providers.isEmpty()) {
            if (fallback != null) callback.onLocation(fallback, true);
            else callback.onError("No location source is available");
            return;
        }

        listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                cancel();
                callback.onLocation(location, false);
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
            if (fallback != null) callback.onLocation(fallback, true);
            else callback.onError("Deckscape could not start the location request");
            return;
        }
        requesting = true;
        timeout = () -> {
            cancel();
            if (fallback != null) callback.onLocation(fallback, true);
            else callback.onError("No location was found within one minute. Try again with a clear view of the sky");
        };
        handler.postDelayed(timeout, LocationFixPolicy.REQUEST_TIMEOUT_MS);
    }

    /** Returns whether an active provider request can currently be cancelled. */
    boolean isRequesting() {
        return requesting;
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
        requesting = false;
    }

    private List<String> enabledProviders() {
        List<String> result = new ArrayList<>();
        for (String provider : providerNames()) {
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
        for (String provider : providerNames()) {
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

    private static String[] providerNames() {
        return new String[]{FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER,
                LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER};
    }
}
