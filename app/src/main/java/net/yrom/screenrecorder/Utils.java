/*
 * Copyright (c) 2017 Yrom Wang <http://www.yrom.net>
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

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.SparseArray;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public class Utils {

    private static SparseArray<String> sAACProfiles = new SparseArray<>();
    private static SparseArray<String> sAVCProfiles = new SparseArray<>();
    private static SparseArray<String> sAVCLevels = new SparseArray<>();
    private static SparseArray<String> sColorFormats = new SparseArray<>();

    public static void findEncodersByTypeAsync(String mimeType, Callback callback) {
        new EncoderFinder(callback).execute(mimeType);
    }

    /**
     * Find an encoder supported specified MIME type
     *
     * @return Returns empty array if not found any encoder supported specified MIME type
     */
    @NonNull
    public static MediaCodecInfo[] findEncodersByType(String mimeType) {
        MediaCodecList codecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
        List<MediaCodecInfo> codecs = new ArrayList<>();
        for (MediaCodecInfo codec : codecList.getCodecInfos()) {
            if (!codec.isEncoder()) {
                continue;
            }
            try {
                MediaCodecInfo.CodecCapabilities cap = codec.getCapabilitiesForType(mimeType);
                if (cap == null) continue;
            } catch (IllegalArgumentException e) {
                // unsupported
                continue;
            }
            codecs.add(codec);
        }

        return codecs.toArray(new MediaCodecInfo[codecs.size()]);
    }

    /**
     * @param avcProfileLevel AVC CodecProfileLevel
     */
    @NonNull
    public static String avcProfileLevelToString(@NonNull MediaCodecInfo.CodecProfileLevel avcProfileLevel) {
        if (sAVCProfiles.size() == 0 || sAVCLevels.size() == 0) {
            initProfileLevels();
        }
        String profile = null, level = null;
        int i = sAVCProfiles.indexOfKey(avcProfileLevel.profile);
        if (i >= 0) {
            profile = sAVCProfiles.valueAt(i);
        }

        i = sAVCLevels.indexOfKey(avcProfileLevel.level);
        if (i >= 0) {
            level = sAVCLevels.valueAt(i);
        }

        if (profile == null) {
            profile = String.valueOf(avcProfileLevel.profile);
        }
        if (level == null) {
            level = String.valueOf(avcProfileLevel.level);
        }
        return profile + '-' + level;
    }

    @NonNull
    public static String[] aacProfiles() {
        if (sAACProfiles.size() == 0) {
            initProfileLevels();
        }
        String[] profiles = new String[sAACProfiles.size()];
        for (int i = 0; i < sAACProfiles.size(); i++) {
            profiles[i] = sAACProfiles.valueAt(i);
        }
        return profiles;
    }

    @Nullable
    public static MediaCodecInfo.CodecProfileLevel toProfileLevel(@NonNull String str) {
        if (sAVCProfiles.size() == 0 || sAVCLevels.size() == 0 || sAACProfiles.size() == 0) {
            initProfileLevels();
        }
        String profile = str;
        String level = null;
        int i = str.indexOf('-');
        if (i > 0) { // AVC profile has level
            profile = str.substring(0, i);
            level = str.substring(i + 1);
        }

        MediaCodecInfo.CodecProfileLevel res = new MediaCodecInfo.CodecProfileLevel();
        if (profile.startsWith("AVC")) {
            res.profile = keyOfValue(sAVCProfiles, profile);
        } else if (profile.startsWith("AAC")) {
            res.profile = keyOfValue(sAACProfiles, profile);
        } else {
            try {
                res.profile = Integer.parseInt(profile);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        if (level != null) {
            if (level.startsWith("AVC")) {
                res.level = keyOfValue(sAVCLevels, level);
            } else {
                try {
                    res.level = Integer.parseInt(level);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }

        return res.profile > 0 && res.level >= 0 ? res : null;
    }

    private static <T> int keyOfValue(@NonNull SparseArray<T> array, @NonNull T value) {
        int size = array.size();
        for (int i = 0; i < size; i++) {
            T t = array.valueAt(i);
            if (t == value || t.equals(value)) {
                return array.keyAt(i);
            }
        }
        return -1;
    }

    private static void initProfileLevels() {
        Field[] fields = MediaCodecInfo.CodecProfileLevel.class.getFields();
        for (Field f : fields) {
            if ((f.getModifiers() & (Modifier.STATIC | Modifier.FINAL)) == 0) {
                continue;
            }
            String name = f.getName();
            SparseArray<String> target;
            if (name.startsWith("AVCProfile")) {
                target = sAVCProfiles;
            } else if (name.startsWith("AVCLevel")) {
                target = sAVCLevels;
            } else if (name.startsWith("AACObject")) {
                target = sAACProfiles;
            } else {
                continue;
            }
            try {
                target.put(f.getInt(null), name);
            } catch (IllegalAccessException e) {
                //ignored
            }
        }
    }

    @NonNull
    public static String toHumanReadable(int colorFormat) {
        if (sColorFormats.size() == 0) {
            initColorFormatFields();
        }
        int i = sColorFormats.indexOfKey(colorFormat);
        if (i >= 0) return sColorFormats.valueAt(i);
        return "0x" + Integer.toHexString(colorFormat);
    }

    private static void initColorFormatFields() {
        // COLOR_
        Field[] fields = MediaCodecInfo.CodecCapabilities.class.getFields();
        for (Field f : fields) {
            if ((f.getModifiers() & (Modifier.STATIC | Modifier.FINAL)) == 0) {
                continue;
            }
            String name = f.getName();
            if (name.startsWith("COLOR_")) {
                try {
                    int value = f.getInt(null);
                    sColorFormats.put(value, name);
                } catch (IllegalAccessException e) {
                    // ignored
                }
            }
        }

    }

    interface Callback {
        void onResult(@NonNull MediaCodecInfo[] codecs);
    }

    public static final class EncoderFinder extends AsyncTask<String, Void, MediaCodecInfo[]> {
        private Callback func;

        EncoderFinder(Callback func) {
            this.func = func;
        }

        @Override
        protected MediaCodecInfo[] doInBackground(String... mimeTypes) {
            return findEncodersByType(mimeTypes[0]);
        }

        @Override
        protected void onPostExecute(MediaCodecInfo[] codecs) {
            func.onResult(codecs);
        }
    }
}
