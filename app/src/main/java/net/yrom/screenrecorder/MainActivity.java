/*
 * Copyright (c) 2014 Yrom Wang <http://www.yrom.net>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.yrom.screenrecorder;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.MediaCodecInfo;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import android.widget.ToggleButton;

import net.yrom.screenrecorder.view.NamedSpinner;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.os.Build.VERSION_CODES.M;
import static net.yrom.screenrecorder.ScreenRecorder.VIDEO_AVC;

public class MainActivity extends Activity {
    private static final int REQUEST_MEDIA_PROJECTION = 1;
    private static final int REQUEST_PERMISSIONS = 2;
    private static final String TAG = "MainActivity";
    // members below will be initialized in onCreate()
    private MediaProjectionManager mMediaProjectionManager;
    private Button mButton;
    private ToggleButton mAudioToggle;
    private NamedSpinner mVideoResolution;
    private NamedSpinner mVideoBitrate;
    private NamedSpinner mOrientation;
    private MediaCodecInfo[] mAvcCodecs; // avc codecs
    /**
     * <b>NOTE:</b>
     * {@code ScreenRecorder} should run in background Service
     * instead of a foreground Activity in this demonstrate.
     */
    private ScreenRecorder mRecorder;

    @NonNull
    private static File getSavingDir() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "ScreenCaptures");
    }

    /**
     * Print information of all MediaCodec on this device.
     */
    private static void logCodecs(@NonNull MediaCodecInfo[] codecs, @NonNull String mimeType) {
        for (MediaCodecInfo codec : codecs) {
            StringBuilder builder = new StringBuilder(512);
            MediaCodecInfo.CodecCapabilities caps = codec.getCapabilitiesForType(mimeType);
            builder.append("Encoder '").append(codec.getName()).append('\'')
                    .append("\n  supported : ")
                    .append(Arrays.toString(codec.getSupportedTypes()));
            MediaCodecInfo.VideoCapabilities videoCaps = caps.getVideoCapabilities();
            if (videoCaps != null) {
                builder.append("\n  Video capabilities:")
                        .append("\n  Widths: ").append(videoCaps.getSupportedWidths())
                        .append("\n  Heights: ").append(videoCaps.getSupportedHeights())
                        .append("\n  Frame Rates: ").append(videoCaps.getSupportedFrameRates())
                        .append("\n  Bitrate: ").append(videoCaps.getBitrateRange());
                if (VIDEO_AVC.equals(mimeType)) {
                    MediaCodecInfo.CodecProfileLevel[] levels = caps.profileLevels;

                    builder.append("\n  Profile-levels: ");
                    for (MediaCodecInfo.CodecProfileLevel level : levels) {
                        builder.append("\n  ").append(Utils.avcProfileLevelToString(level));
                    }
                }
                builder.append("\n  Color-formats: ");
                for (int c : caps.colorFormats) {
                    builder.append("\n  ").append(Utils.toHumanReadable(c));
                }
            }
            MediaCodecInfo.AudioCapabilities audioCaps = caps.getAudioCapabilities();
            if (audioCaps != null) {
                builder.append("\n Audio capabilities:")
                        .append("\n Sample Rates: ").append(Arrays.toString(audioCaps.getSupportedSampleRates()))
                        .append("\n Bit Rates: ").append(audioCaps.getBitrateRange())
                        .append("\n Max channels: ").append(audioCaps.getMaxInputChannelCount());
            }
            Log.i("@@@", builder.toString());
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mOrientation.setSelectedPosition(1);
        } else {
            mOrientation.setSelectedPosition(0);
        }
        // reset padding
        int horizontal = (int) getResources().getDimension(R.dimen.activity_horizontal_margin);
        int vertical = (int) getResources().getDimension(R.dimen.activity_vertical_margin);
        findViewById(R.id.container).setPadding(horizontal, vertical, horizontal, vertical);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            // NOTE: Should pass this result data into a Service to run ScreenRecorder.
            // The following codes are merely exemplary.

            MediaProjection mediaProjection = mMediaProjectionManager.getMediaProjection(resultCode, data);
            if (mediaProjection == null) {
                Log.e("@@", "media projection is null");
                return;
            }

            VideoEncodeConfig video = createVideoConfig();
            AudioEncodeConfig audio = createAudioConfig(); // audio can be null

            File dir = getSavingDir();
            if (!dir.exists() && !dir.mkdirs()) {
                cancelRecorder();
                return;
            }
            SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US);
            final File file = new File(dir, format.format(new Date())
                    + "-w" + video.getWidth() + "-h" + video.getHeight()
                    + "-b" + video.getBitrate()
                    + "-f" + video.getFrameRate()
                    + ".mp4");
            Log.d("@@", "Create recorder with :" + video + " \n " + audio + "\n " + file);
            mRecorder = newRecorder(mediaProjection, video, audio, file);
            if (hasPermissions()) {
                startRecorder();
                // automatically stop record for test
                if (BuildConfig.DEBUG) {
                    new Handler(Looper.getMainLooper()).postDelayed(this::stopRecorder, 5000);
                }
            } else {
                cancelRecorder();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mMediaProjectionManager = (MediaProjectionManager) getApplicationContext().getSystemService(MEDIA_PROJECTION_SERVICE);

        bindViews();

        Utils.findEncodersByTypeAsync(VIDEO_AVC, codecs -> {
            logCodecs(codecs, VIDEO_AVC);
            mAvcCodecs = codecs;
            restoreSelections(mVideoResolution, mVideoBitrate);

        });

        mAudioToggle.setChecked(
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                        .getBoolean(getResources().getResourceEntryName(mAudioToggle.getId()), true));
    }

    @NonNull
    private ScreenRecorder newRecorder(@NonNull MediaProjection mediaProjection,
                                       @NonNull VideoEncodeConfig video,
                                       @Nullable AudioEncodeConfig audio,
                                       @NonNull File output) {
        ScreenRecorder r = new ScreenRecorder(video, audio,
                1, mediaProjection, output.getAbsolutePath());
        r.setCallback(new ScreenRecorder.Callback() {
            private static final String TAG = "ScreenRecorder.Callback";
            long startTime = 0;

            @Override
            public void onStop(Throwable error) {
                runOnUiThread(() -> stopRecorder());
                if (error != null) {
                    toast("Recorder error ! See logcat for more details");
                    error.printStackTrace();
                    //noinspection ResultOfMethodCallIgnored
                    output.delete();
                } else {
                    Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                            .addCategory(Intent.CATEGORY_DEFAULT)
                            .setData(Uri.fromFile(output));
                    sendBroadcast(intent);
                }
            }

            @Override
            public void onStart() {
                Log.d(TAG, "onStart: ");
            }

            @Override
            public void onRecording(long presentationTimeUs) {
                if (startTime <= 0) {
                    startTime = presentationTimeUs;
                }
//                long time = (presentationTimeUs - startTime) / 1000;
//                Log.d(TAG, "onRecording: " + time);// VERBOSE
            }
        });
        return r;
    }

    @Nullable
    private AudioEncodeConfig createAudioConfig() {
        if (!mAudioToggle.isChecked()) return null;
        return new AudioEncodeConfig();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        saveSelections();
        stopRecorder();
    }

    private void startCaptureIntent() {
        Intent captureIntent = mMediaProjectionManager.createScreenCaptureIntent();
        startActivityForResult(captureIntent, REQUEST_MEDIA_PROJECTION);
    }

    @NonNull
    private VideoEncodeConfig createVideoConfig() {
        // video size
        int[] selectedWithHeight = getSelectedWithHeight();
        boolean isLandscape = isLandscape();
        int width = selectedWithHeight[isLandscape ? 0 : 1];
        int height = selectedWithHeight[isLandscape ? 1 : 0];
        int frameRate = getSelectedFrameRate();
        int bitrate = getSelectedVideoBitrate();
        return new VideoEncodeConfig(width, height, bitrate, frameRate);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_PERMISSIONS) {
            int granted = PackageManager.PERMISSION_GRANTED;
            for (int r : grantResults) {
                granted |= r;
            }
            if (granted == PackageManager.PERMISSION_GRANTED) {
                startCaptureIntent();
            } else {
                toast("No Permission!");
            }
        }
    }

    private void bindViews() {
        mButton = findViewById(R.id.record_button);
        mButton.setOnClickListener(this::onButtonClick);

        mVideoResolution = findViewById(R.id.resolution);
        mVideoBitrate = findViewById(R.id.video_bitrate);
        mOrientation = findViewById(R.id.orientation);

        mAudioToggle = findViewById(R.id.with_audio);

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mOrientation.setSelectedPosition(1);
        }

        mVideoResolution.setOnItemSelectedListener((view, position) -> {
            if (position == 0) return;
            onResolutionChanged(position, view.getSelectedItem());
        });

        mVideoBitrate.setOnItemSelectedListener((view, position) -> {
            if (position == 0) return;
            onBitrateChanged(position, view.getSelectedItem());
        });
        mOrientation.setOnItemSelectedListener((view, position) -> {
            if (position == 0) return;
            onOrientationChanged(position, view.getSelectedItem());
        });
    }

    private void onButtonClick(@SuppressWarnings("unused") View ignored) {
        if (mRecorder != null) {
            stopRecorder();
        } else if (hasPermissions()) {
            startCaptureIntent();
        } else if (Build.VERSION.SDK_INT >= M) {
            requestPermissions();
        } else {
            toast("No permission to write sd card");
        }
    }

    private void cancelRecorder() {
        if (mRecorder == null) return;
        Toast.makeText(this, "Permission denied! Screen recorder is cancel", Toast.LENGTH_SHORT).show();
        stopRecorder();
    }

    @TargetApi(M)
    private void requestPermissions() {
        String[] permissions = mAudioToggle.isChecked()
                ? new String[]{WRITE_EXTERNAL_STORAGE, RECORD_AUDIO}
                : new String[]{WRITE_EXTERNAL_STORAGE};
        boolean showRationale = false;
        for (String perm : permissions) {
            showRationale |= shouldShowRequestPermissionRationale(perm);
        }
        if (!showRationale) {
            requestPermissions(permissions, REQUEST_PERMISSIONS);
            return;
        }
        new AlertDialog.Builder(this)
                .setMessage("Using your mic to record audio and your sd card to save video file")
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
                        requestPermissions(permissions, REQUEST_PERMISSIONS))
                .setNegativeButton(android.R.string.cancel, null)
                .create()
                .show();
    }

    private boolean hasPermissions() {
        PackageManager pm = getPackageManager();
        String packageName = getPackageName();
        int granted = (mAudioToggle.isChecked() ? pm.checkPermission(RECORD_AUDIO, packageName) : PackageManager.PERMISSION_GRANTED)
                | pm.checkPermission(WRITE_EXTERNAL_STORAGE, packageName);
        return granted == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("SetTextI18n")
    private void startRecorder() {
        if (mRecorder == null) return;
        mRecorder.start();
        mButton.setText("Stop Recorder");
        moveTaskToBack(true);
    }

    @SuppressLint("SetTextI18n")
    private void stopRecorder() {
        if (mRecorder != null) {
            mRecorder.quit();
        }
        mRecorder = null;
        mButton.setText("Restart recorder");
    }

    private void onResolutionChanged(int selectedPosition, @NonNull String resolution) {
        String codecName = getSelectedVideoCodec();
        MediaCodecInfo codec = getVideoCodecInfo(codecName);
        if (codec == null) return;
        MediaCodecInfo.CodecCapabilities capabilities = codec.getCapabilitiesForType(VIDEO_AVC);
        MediaCodecInfo.VideoCapabilities videoCapabilities = capabilities.getVideoCapabilities();
        String[] xes = resolution.split("x");
        if (xes.length != 2) throw new IllegalArgumentException();
        boolean isLandscape = isLandscape();
        int width = Integer.parseInt(xes[isLandscape ? 0 : 1]);
        int height = Integer.parseInt(xes[isLandscape ? 1 : 0]);

        double selectedFramerate = getSelectedFrameRate();
        int resetPos = Math.max(selectedPosition - 1, 0);
        if (!videoCapabilities.isSizeSupported(width, height)) {
            mVideoResolution.setSelectedPosition(resetPos);
            toast("codec '%s' unsupported size %dx%d (%s)",
                    codecName, width, height, mOrientation.getSelectedItem());
            Log.w("@@", codecName +
                    " height range: " + videoCapabilities.getSupportedHeights() +
                    "\n width range: " + videoCapabilities.getSupportedHeights());
        } else if (!videoCapabilities.areSizeAndRateSupported(width, height, selectedFramerate)) {
            mVideoResolution.setSelectedPosition(resetPos);
            toast("codec '%s' unsupported size %dx%d(%s)\nwith frameRate %d",
                    codecName, width, height, mOrientation.getSelectedItem(), (int) selectedFramerate);
        }
    }

    private void onBitrateChanged(int selectedPosition, @NonNull String bitrate) {
        String codecName = getSelectedVideoCodec();
        MediaCodecInfo codec = getVideoCodecInfo(codecName);
        if (codec == null) return;
        MediaCodecInfo.CodecCapabilities capabilities = codec.getCapabilitiesForType(VIDEO_AVC);
        MediaCodecInfo.VideoCapabilities videoCapabilities = capabilities.getVideoCapabilities();
        int selectedBitrate = Integer.parseInt(bitrate) * 1000;

        int resetPos = Math.max(selectedPosition - 1, 0);
        if (!videoCapabilities.getBitrateRange().contains(selectedBitrate)) {
            mVideoBitrate.setSelectedPosition(resetPos);
            toast("codec '%s' unsupported bitrate %d", codecName, selectedBitrate);
            Log.w("@@", codecName +
                    " bitrate range: " + videoCapabilities.getBitrateRange());
        }
    }

    private void onOrientationChanged(int selectedPosition, @NonNull String orientation) {
        String codecName = getSelectedVideoCodec();
        MediaCodecInfo codec = getVideoCodecInfo(codecName);
        if (codec == null) return;
        MediaCodecInfo.CodecCapabilities capabilities = codec.getCapabilitiesForType(VIDEO_AVC);
        MediaCodecInfo.VideoCapabilities videoCapabilities = capabilities.getVideoCapabilities();
        int[] selectedWithHeight = getSelectedWithHeight();
        boolean isLandscape = selectedPosition == 1;
        int width = selectedWithHeight[isLandscape ? 0 : 1];
        int height = selectedWithHeight[isLandscape ? 1 : 0];
        int resetPos = Math.max(mVideoResolution.getSelectedItemPosition() - 1, 0);
        if (!videoCapabilities.isSizeSupported(width, height)) {
            mVideoResolution.setSelectedPosition(resetPos);
            toast("codec '%s' unsupported size %dx%d (%s)",
                    codecName, width, height, orientation);
            return;
        }

        int current = getResources().getConfiguration().orientation;
        if (isLandscape && current == Configuration.ORIENTATION_PORTRAIT) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else if (!isLandscape && current == Configuration.ORIENTATION_PORTRAIT) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }

    @Nullable
    private MediaCodecInfo getVideoCodecInfo(@Nullable String codecName) {
        if (codecName == null) return null;
        if (mAvcCodecs == null) {
            mAvcCodecs = Utils.findEncodersByType(VIDEO_AVC);
        }
        MediaCodecInfo codec = null;
        for (MediaCodecInfo info : mAvcCodecs) {
            if (info.getName().equals(codecName)) {
                codec = info;
                break;
            }
        }
        if (codec == null) return null;
        return codec;
    }

    private boolean isLandscape() {
        return mOrientation != null && mOrientation.getSelectedItemPosition() == 1;
    }

    @NonNull
    private String getSelectedVideoCodec() {
        return "OMX.qcom.video.encoder.avc";
    }

    private int getSelectedVideoBitrate() {
        if (mVideoBitrate == null) throw new IllegalStateException();
        String selectedItem = mVideoBitrate.getSelectedItem(); //kbps
        return Integer.parseInt(selectedItem) * 1000;
    }

    private int getSelectedFrameRate() {
        return 15;
    }

    @NonNull
    private int[] getSelectedWithHeight() {
        if (mVideoResolution == null) throw new IllegalStateException();
        String selected = mVideoResolution.getSelectedItem();
        String[] xes = selected.split("x");
        if (xes.length != 2) throw new IllegalArgumentException();
        return new int[]{Integer.parseInt(xes[0]), Integer.parseInt(xes[1])};

    }

    private void toast(@NonNull String message, @NonNull Object... args) {
        Toast toast = Toast.makeText(this,
                (args.length == 0) ? message : String.format(Locale.US, message, args),
                Toast.LENGTH_SHORT);
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(toast::show);
        } else {
            toast.show();
        }
    }

    private void restoreSelections(@NonNull NamedSpinner... spinners) {
        SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        for (NamedSpinner spinner : spinners) {
            restoreSelectionFromPreferences(preferences, spinner);
        }
    }

    private void saveSelections() {
        SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor edit = preferences.edit();
        for (NamedSpinner spinner : new NamedSpinner[]{
                mVideoResolution,
                mVideoBitrate,
        }) {
            saveSelectionToPreferences(edit, spinner);
        }
        edit.putBoolean(getResources().getResourceEntryName(mAudioToggle.getId()), mAudioToggle.isChecked());
        edit.apply();
    }

    private void saveSelectionToPreferences(@NonNull SharedPreferences.Editor preferences, @NonNull NamedSpinner spinner) {
        int resId = spinner.getId();
        String key = getResources().getResourceEntryName(resId);
        int selectedItemPosition = spinner.getSelectedItemPosition();
        if (selectedItemPosition >= 0) {
            preferences.putInt(key, selectedItemPosition);
        }
    }

    private void restoreSelectionFromPreferences(@NonNull SharedPreferences preferences, @NonNull NamedSpinner spinner) {
        int resId = spinner.getId();
        String key = getResources().getResourceEntryName(resId);
        int value = preferences.getInt(key, -1);
        if (value >= 0 && spinner.getAdapter() != null) {
            spinner.setSelectedPosition(value);
        }
    }

}
