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
public class VideoEncodeConfig {
    private final int width;
    private final int height;
    private final int bitrate;
    private final int frameRate;
    @NonNull
    private final String codecName;
    @NonNull
    private final String mimeType;

    /**
     */
    public VideoEncodeConfig(int width,
                             int height,
                             int bitrate,
                             int frameRate) {
        this.width = width;
        this.height = height;
        this.bitrate = bitrate;
        this.frameRate = frameRate;
        this.codecName = "OMX.qcom.video.encoder.avc";
        this.mimeType = MediaFormat.MIMETYPE_VIDEO_AVC;
    }

    @NonNull
    public MediaFormat toFormat() {
        MediaFormat format = MediaFormat.createVideoFormat(mimeType, width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, frameRate);
        // maybe useful
        // format.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 10_000_000);
        return format;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getBitrate() {
        return bitrate;
    }

    public int getFrameRate() {
        return frameRate;
    }

    @NonNull
    public String getCodecName() {
        return codecName;
    }

    @NonNull
    public String getMimeType() {
        return mimeType;
    }


    @Override
    public String toString() {
        return "VideoEncodeConfig{" +
                "width=" + width +
                ", height=" + height +
                ", bitrate=" + bitrate +
                ", frameRate=" + frameRate +
                ", codecName='" + codecName + '\'' +
                ", mimeType='" + mimeType + '\'' +
                '}';
    }
}
