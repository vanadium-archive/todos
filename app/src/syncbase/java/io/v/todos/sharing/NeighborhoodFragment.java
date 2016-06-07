package io.v.todos.sharing;


import android.app.Fragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.SwitchCompat;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

import io.v.todos.R;
import io.v.todos.persistence.syncbase.SyncbasePersistence;
import io.v.v23.context.VContext;
import io.v.v23.discovery.Advertisement;
import io.v.v23.verror.VException;

/**
 * A fragment encapsulating menu options and functionality related to list sharing.
 */
public class NeighborhoodFragment extends Fragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String
            FRAGMENT_TAG = NeighborhoodFragment.class.getSimpleName();

    private static final String PREF_ADVERTISE_NEIGHBORHOOD = "advertise neighborhood";

    private static SharedPreferences sPrefs;
    private static VContext sAdvertiseContext;
    private static Advertisement sAd;

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
    public static void initSharePresence() {
        sPrefs = sPrefs != null ? sPrefs : PreferenceManager.getDefaultSharedPreferences
                (SyncbasePersistence.getAppContext());

        sAd = new Advertisement();
        sAd.setInterfaceName(Sharing.getPresenceInterface());
        // TODO(alexfandrianto): Revisit why we must put an address inside the advertisement.
        // If we are advertising our presence, then there isn't any need for it.
        // For now, put our email address in the addresses, despite it being an attribute.
        sAd.getAddresses().add(SyncbasePersistence.getPersonalEmail());

        sPrefs.registerOnSharedPreferenceChangeListener(sSharedPrefListener);
        updateSharePresence();
    }

    private static void updateSharePresence() {
        if (isAdvertising()) {
            if (sAdvertiseContext == null) {
                sAdvertiseContext = SyncbasePersistence.getAppVContext().withCancel();
                try {
                    Futures.addCallback(Sharing.getDiscovery().advertise(sAdvertiseContext, sAd,
                            // TODO(rosswang): Restrict to contacts only. However, per discussion
                            // with mattr@ and ashankar@, this could be a scalability challenge as
                            // the advertisement would need to be IB-encrypted for each possible
                            // recipient. This can be broken into multiple advertisements but at the
                            // increased risk of over-the-air hash collision.
                            null),
                            new FutureCallback<Void>() {
                                @Override
                                public void onSuccess(@Nullable Void result) {
                                }

                                @Override
                                public void onFailure(@NonNull Throwable t) {
                                    handleAdvertisingError(t);
                                }
                            });
                } catch (VException e) {
                    handleAdvertisingError(e);
                }
            }
        } else if (sAdvertiseContext != null) {
            sAdvertiseContext.cancel();
            sAdvertiseContext = null;
        }
    }

    private static void handleAdvertisingError(Throwable t) {
        SyncbasePersistence.getAppErrorReporter().onError(R.string.err_share_location, t);
        setAdvertiseNeighborhood(false);
    }

    private static void setAdvertiseNeighborhood(boolean value) {
        sPrefs.edit()
                .putBoolean(PREF_ADVERTISE_NEIGHBORHOOD, value)
                .apply();
    }

    private static boolean isAdvertising() {
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
        boolean value = isAdvertising();
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
