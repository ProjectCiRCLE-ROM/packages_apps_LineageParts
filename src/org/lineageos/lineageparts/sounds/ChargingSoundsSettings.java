/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.lineageparts.sounds;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.ArraySet;
import android.util.Log;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.preference.Preference;

import org.lineageos.lineageparts.R;
import org.lineageos.lineageparts.SettingsPreferenceFragment;
import org.lineageos.lineageparts.search.BaseSearchIndexProvider;
import org.lineageos.lineageparts.search.Searchable;

import java.util.Set;

public class ChargingSoundsSettings
        extends SettingsPreferenceFragment implements Searchable {
    private static final String TAG = "ChargingSoundsSettings";

    private static final String KEY_CHARGING_VIBRATION_ENABLED = "charging_vibration_enabled";
    private static final String KEY_WIRELESS_CHARGING_SOUNDS = "wireless_charging_sounds";
    private static final String KEY_CHARGING_SOUND_LOW = "charging_sound_low";
    private static final String KEY_CHARGING_SOUND_MEDIUM = "charging_sound_medium";
    private static final String KEY_CHARGING_SOUND_HIGH = "charging_sound_high";

    // Used for power notification uri string if set to silent
    private static final String RINGTONE_SILENT_URI_STRING = "silent";

    private static final String DEFAULT_WIRELESS_CHARGING_SOUND =
            "/product/media/audio/ui/WirelessChargingStarted.ogg";
    private static final String DEFAULT_CHARGING_SOUND_LOW =
            "/product/media/audio/ui/charging_started_low.flac";
    private static final String DEFAULT_CHARGING_SOUND_MEDIUM =
            "/product/media/audio/ui/charging_started_medium.flac";
    private static final String DEFAULT_CHARGING_SOUND_HIGH =
            "/product/media/audio/ui/charging_started_high.flac";

    private static final int REQUEST_CODE_WIRELESS_CHARGING_SOUND = 1;
    private static final int REQUEST_CODE_CHARGING_SOUND_LOW = 2;
    private static final int REQUEST_CODE_CHARGING_SOUND_MEDIUM = 3;
    private static final int REQUEST_CODE_CHARGING_SOUND_HIGH = 4;

    private Preference mWirelessChargingSounds;
    private Preference mChargingSoundLow;
    private Preference mChargingSoundMedium;
    private Preference mChargingSoundHigh;

    private Uri mDefaultWirelessChargingSoundUri;
    private Uri mDefaultChargingSoundLowUri;
    private Uri mDefaultChargingSoundMediumUri;
    private Uri mDefaultChargingSoundHighUri;

    private int mRequestCode;

    private final ActivityResultLauncher<Intent> mActivityResultLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != Activity.RESULT_OK) {
                            return;
                        }
                        Intent data = result.getData();
                        if (data == null) {
                            return;
                        }
                        Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                                Uri.class);

                        if (uri == null) {
                            updateSound(RINGTONE_SILENT_URI_STRING, mRequestCode);
                            return;
                        }

                        String mimeType = requireContext().getContentResolver().getType(uri);
                        if (mimeType == null) {
                            Log.e(TAG, "call to updateSound for URI:" + uri
                                    + " ignored: failure to find mimeType "
                                    + "(no access from this context?)");
                            return;
                        }

                        if (!isSupportedMimeType(mimeType)) {
                            Log.e(TAG, "call to updateSound for URI:" + uri
                                    + " ignored: associated mimeType:" + mimeType
                                    + " is not an audio type");
                            return;
                        }

                        updateSound(uri.toString(), mRequestCode);
                    });

    private boolean isSupportedMimeType(String mimeType) {
        return mimeType.startsWith("audio/") || mimeType.equals("application/ogg");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.charging_sounds_settings);

        Vibrator vibrator = requireActivity().getSystemService(Vibrator.class);
        if (vibrator == null || !vibrator.hasVibrator()) {
            removePreference(KEY_CHARGING_VIBRATION_ENABLED);
        }

        mWirelessChargingSounds = findPreference(KEY_WIRELESS_CHARGING_SOUNDS);
        mChargingSoundLow = findPreference(KEY_CHARGING_SOUND_LOW);
        mChargingSoundMedium = findPreference(KEY_CHARGING_SOUND_MEDIUM);
        mChargingSoundHigh = findPreference(KEY_CHARGING_SOUND_HIGH);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mDefaultWirelessChargingSoundUri = audioFileToUri(requireContext(),
                DEFAULT_WIRELESS_CHARGING_SOUND);
        mDefaultChargingSoundLowUri = audioFileToUri(requireContext(),
                DEFAULT_CHARGING_SOUND_LOW);
        mDefaultChargingSoundMediumUri = audioFileToUri(requireContext(),
                DEFAULT_CHARGING_SOUND_MEDIUM);
        mDefaultChargingSoundHighUri = audioFileToUri(requireContext(),
                DEFAULT_CHARGING_SOUND_HIGH);

        updateSound(Settings.Global.getString(getContentResolver(),
                Settings.Global.WIRELESS_CHARGING_STARTED_SOUND),
                REQUEST_CODE_WIRELESS_CHARGING_SOUND);
        updateSound(Settings.Global.getString(getContentResolver(),
                Settings.Global.CHARGING_STARTED_SOUND_LOW),
                REQUEST_CODE_CHARGING_SOUND_LOW);
        updateSound(Settings.Global.getString(getContentResolver(),
                Settings.Global.CHARGING_STARTED_SOUND_MEDIUM),
                REQUEST_CODE_CHARGING_SOUND_MEDIUM);
        updateSound(Settings.Global.getString(getContentResolver(),
                Settings.Global.CHARGING_STARTED_SOUND_HIGH),
                REQUEST_CODE_CHARGING_SOUND_HIGH);
    }

    private Uri audioFileToUri(@NonNull Context context, String audioFile) {
        Cursor cursor = context.getContentResolver().query(
                MediaStore.Audio.Media.INTERNAL_CONTENT_URI,
                new String[] { MediaStore.Audio.Media._ID },
                MediaStore.Audio.Media.DATA + "=? ",
                new String[] { audioFile }, null);
        if (cursor == null) {
            return null;
        }
        if (!cursor.moveToFirst()) {
            cursor.close();
            return null;
        }
        int id = cursor.getInt(cursor.getColumnIndex(MediaStore.MediaColumns._ID));
        cursor.close();
        return Uri.withAppendedPath(MediaStore.Audio.Media.INTERNAL_CONTENT_URI,
                Integer.toString(id));
    }

    private void updateSound(String toneUriString, int requestCode) {
        final String defaultPath;
        final Uri defaultUri;
        final String settingKey;
        final Preference pref;

        switch (requestCode) {
            case REQUEST_CODE_WIRELESS_CHARGING_SOUND:
                defaultPath = DEFAULT_WIRELESS_CHARGING_SOUND;
                defaultUri = mDefaultWirelessChargingSoundUri;
                settingKey = Settings.Global.WIRELESS_CHARGING_STARTED_SOUND;
                pref = mWirelessChargingSounds;
                break;
            case REQUEST_CODE_CHARGING_SOUND_LOW:
                defaultPath = DEFAULT_CHARGING_SOUND_LOW;
                defaultUri = mDefaultChargingSoundLowUri;
                settingKey = Settings.Global.CHARGING_STARTED_SOUND_LOW;
                pref = mChargingSoundLow;
                break;
            case REQUEST_CODE_CHARGING_SOUND_MEDIUM:
                defaultPath = DEFAULT_CHARGING_SOUND_MEDIUM;
                defaultUri = mDefaultChargingSoundMediumUri;
                settingKey = Settings.Global.CHARGING_STARTED_SOUND_MEDIUM;
                pref = mChargingSoundMedium;
                break;
            case REQUEST_CODE_CHARGING_SOUND_HIGH:
            default:
                defaultPath = DEFAULT_CHARGING_SOUND_HIGH;
                defaultUri = mDefaultChargingSoundHighUri;
                settingKey = Settings.Global.CHARGING_STARTED_SOUND_HIGH;
                pref = mChargingSoundHigh;
                break;
        }

        if ((toneUriString == null || toneUriString.equals(defaultPath))
                && defaultUri != null) {
            toneUriString = defaultUri.toString();
        }

        final String toneTitle;
        if (toneUriString != null && !toneUriString.equals(RINGTONE_SILENT_URI_STRING)) {
            final Ringtone ringtone = RingtoneManager.getRingtone(getActivity(),
                    Uri.parse(toneUriString));
            if (ringtone != null) {
                toneTitle = ringtone.getTitle(getActivity());
            } else {
                toneTitle = "";
                toneUriString = Settings.System.DEFAULT_NOTIFICATION_URI.toString();
            }
        } else {
            toneTitle = getString(R.string.charging_sounds_ringtone_silent);
            toneUriString = RINGTONE_SILENT_URI_STRING;
        }

        pref.setSummary(toneTitle);
        Settings.Global.putString(getContentResolver(), settingKey, toneUriString);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference == mWirelessChargingSounds) {
            launchSoundPicker(REQUEST_CODE_WIRELESS_CHARGING_SOUND,
                    getString(R.string.wireless_charging_sounds_title),
                    mDefaultWirelessChargingSoundUri,
                    Settings.Global.getString(getContentResolver(),
                            Settings.Global.WIRELESS_CHARGING_STARTED_SOUND));
        } else if (preference == mChargingSoundLow) {
            launchSoundPicker(REQUEST_CODE_CHARGING_SOUND_LOW,
                    getString(R.string.charging_sound_low_title),
                    mDefaultChargingSoundLowUri,
                    Settings.Global.getString(getContentResolver(),
                            Settings.Global.CHARGING_STARTED_SOUND_LOW));
        } else if (preference == mChargingSoundMedium) {
            launchSoundPicker(REQUEST_CODE_CHARGING_SOUND_MEDIUM,
                    getString(R.string.charging_sound_medium_title),
                    mDefaultChargingSoundMediumUri,
                    Settings.Global.getString(getContentResolver(),
                            Settings.Global.CHARGING_STARTED_SOUND_MEDIUM));
        } else if (preference == mChargingSoundHigh) {
            launchSoundPicker(REQUEST_CODE_CHARGING_SOUND_HIGH,
                    getString(R.string.charging_sound_high_title),
                    mDefaultChargingSoundHighUri,
                    Settings.Global.getString(getContentResolver(),
                            Settings.Global.CHARGING_STARTED_SOUND_HIGH));
        }
        return super.onPreferenceTreeClick(preference);
    }

    private void launchSoundPicker(int requestCode, String title, Uri defaultUri,
            String currentUriString) {
        final Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, title);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, defaultUri);
        if (currentUriString != null && !currentUriString.equals(RINGTONE_SILENT_URI_STRING)) {
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                    Uri.parse(currentUriString));
        }
        mRequestCode = requestCode;
        mActivityResultLauncher.launch(intent);
    }

    public static final Searchable.SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {

        @Override
        public Set<String> getNonIndexableKeys(Context context) {
            final Set<String> result = new ArraySet<>();

            if (!context.getResources().getBoolean(org.lineageos.platform.internal.R.bool
                    .config_deviceSupportsWirelessCharging)) {
                result.add(KEY_WIRELESS_CHARGING_SOUNDS);
            }
            return result;
        }
    };
}
