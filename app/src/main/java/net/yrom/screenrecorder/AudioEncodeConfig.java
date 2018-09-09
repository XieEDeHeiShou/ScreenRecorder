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
import android.media.MediaFormat;
import android.support.annotation.NonNull;

/**
 * @author yrom
 * @version 2017/12/3
 */
public class AudioEncodeConfig {
    private final String codecName;
    private final String mimeType;
    private final int bitRate;
    private final int sampleRate;
    private final int channelCount;
    private final int profile;

    public AudioEncodeConfig() {
        this.codecName = "OMX.google.aac.encoder";
        this.mimeType = MediaFormat.MIMETYPE_AUDIO_AAC;
        this.bitRate = 80;
        this.sampleRate = 44100;
        this.channelCount = 1;
        this.profile = MediaCodecInfo.CodecProfileLevel.AACObjectMain;
    }

    @NonNull
    public MediaFormat toFormat() {
        MediaFormat format = MediaFormat.createAudioFormat(mimeType, sampleRate, channelCount);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, profile);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        //format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 4096 * 4);
        return format;
    }

    public String getCodecName() {
        return codecName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public int getBitRate() {
        return bitRate;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public int getChannelCount() {
        return channelCount;
    }

    public int getProfile() {
        return profile;
    }

    @Override
    public String toString() {
        return "AudioEncodeConfig{" +
                "codecName='" + codecName + '\'' +
                ", mimeType='" + mimeType + '\'' +
                ", bitRate=" + bitRate +
                ", sampleRate=" + sampleRate +
                ", channelCount=" + channelCount +
                ", profile=" + profile +
                '}';
    }
}
