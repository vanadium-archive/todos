package io.v.todos.sharing;

import android.app.Fragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v7.widget.SwitchCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.Toast;

import io.v.syncbase.Syncbase;
import io.v.syncbase.core.VError;
import io.v.todos.R;
import io.v.todos.persistence.syncbase.SyncbasePersistence;
import io.v.v23.context.VContext;
import io.v.v23.discovery.Advertisement;

/**
 * A fragment encapsulating menu options and functionality related to list sharing.
 */
public class NeighborhoodFragment extends Fragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String
            FRAGMENT_TAG = NeighborhoodFragment.class.getSimpleName();

    private static final String PREF_ADVERTISE_NEIGHBORHOOD = "advertise neighborhood";

    private static SharedPreferences sPrefs;

    // This has to be a field because registerOnSharedPreferenceChangeListener does not keep a hard
    // reference to the listener, making it otherwise susceptible to garbage collection.
    private static final SharedPreferences.OnSharedPreferenceChangeListener sSharedPrefListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                                      String key) {
                    if (PREF_ADVERTISE_NEIGHBORHOOD.equals(key)) {
                        updateSharePresence();
                    }
                }
            };

    /**
     * Initializes advertisement of one's presence to the neighborhood and watches shared
     * preferences to toggle sharing on and off as the preference is changed.
     */
    public void initSharePresence(Context androidContext) {
        sPrefs = sPrefs != null ? sPrefs : PreferenceManager.getDefaultSharedPreferences(
                androidContext);

        sPrefs.registerOnSharedPreferenceChangeListener(sSharedPrefListener);
        updateSharePresence();
    }

    private static void updateSharePresence() {
        if (shouldAdvertise()) {
            try {
                Syncbase.advertiseLoggedInUserInNeighborhood();
            } catch (VError vError) {
                Log.w(FRAGMENT_TAG, "Failed to advertise logged in user", vError);
            }
        } else {
            Syncbase.stopAdvertisingLoggedInUserInNeighborhood();
        }
    }

    private static void setAdvertiseNeighborhood(boolean value) {
        sPrefs.edit().putBoolean(PREF_ADVERTISE_NEIGHBORHOOD, value).apply();
    }

    private static boolean shouldAdvertise() {
        return sPrefs.getBoolean(PREF_ADVERTISE_NEIGHBORHOOD, true);
    }

    private SwitchCompat mSwitch;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.neighborhood, menu);
        MenuItem menuItem = menu.findItem(R.id.advertise_neighborhood);
        mSwitch = (SwitchCompat) menuItem.getActionView().
                findViewById(R.id.neighborhood_switch);
        mSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setAdvertiseNeighborhood(isChecked);
            }
        });
        sPrefs = sPrefs != null ? sPrefs : PreferenceManager.getDefaultSharedPreferences
                (getActivity());
        sPrefs.registerOnSharedPreferenceChangeListener(this);
        updateAdvertiseNeighborhoodChecked();
    }

    @Override
    public void onDestroyOptionsMenu() {
        sPrefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (PREF_ADVERTISE_NEIGHBORHOOD.equals(key)) {
            updateAdvertiseNeighborhoodChecked();
        }
    }

    private static Boolean sLastToastCheck; // Used to track the last toasted value.

    private void updateAdvertiseNeighborhoodChecked() {
        boolean value = shouldAdvertise();
        mSwitch.setChecked(value);
        mSwitch.setThumbResource(value ?
                R.drawable.sharing_activated_0_5x :
                R.drawable.sharing_deactivated_0_5x);

        // If this was a change, then toast information to the user.
        if (sLastToastCheck == null || sLastToastCheck != value) {
            sLastToastCheck = value;
            Toast.makeText(getActivity(), value ?
                    R.string.presence_on : R.string.presence_off, Toast.LENGTH_SHORT).show();
        }
    }
}
