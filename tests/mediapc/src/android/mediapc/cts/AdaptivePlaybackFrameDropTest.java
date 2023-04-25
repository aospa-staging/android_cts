/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.mediapc.cts;

import static org.junit.Assert.assertTrue;

import android.media.MediaCodecInfo;
import android.mediapc.cts.common.PerformanceClassEvaluator;
import android.mediapc.cts.common.Utils;

import androidx.test.filters.LargeTest;

import com.android.compatibility.common.util.CddTest;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;

/**
 * The following test class validates the frame drops of AdaptivePlayback for the hardware decoders
 * under the load condition (Transcode + Audio Playback).
 */
@RunWith(Parameterized.class)
public class AdaptivePlaybackFrameDropTest extends FrameDropTestBase {
    private static final String LOG_TAG = AdaptivePlaybackFrameDropTest.class.getSimpleName();

    public AdaptivePlaybackFrameDropTest(String mimeType, String decoderName, boolean isAsync) {
        super(mimeType, decoderName, isAsync);
    }

    @Rule
    public final TestName mTestName = new TestName();

    // Returns the list of parameters with mimeTypes and their hardware decoders supporting the
    // AdaptivePlayback feature combining with sync and async modes.
    // Parameters {0}_{1}_{2} -- Mime_DecoderName_isAsync
    @Parameterized.Parameters(name = "{index}({0}_{1}_{2})")
    public static Collection<Object[]> inputParams() {
        return prepareArgumentsList(new String[]{
                MediaCodecInfo.CodecCapabilities.FEATURE_AdaptivePlayback});
    }

    private int testAdaptivePlaybackFrameDrop(int frameRate) throws Exception {
        String[] testFiles = frameRate == 30 ?
                new String[]{m1080p30FpsTestFiles.get(mMime), m540p30FpsTestFiles.get(mMime)} :
                new String[]{m1080p60FpsTestFiles.get(mMime), m540p60FpsTestFiles.get(mMime)};
        PlaybackFrameDrop playbackFrameDrop = new PlaybackFrameDrop(mMime, mDecoderName, testFiles,
                mSurface, frameRate, mIsAsync);

        return playbackFrameDrop.getFrameDropCount();
    }

    /**
     * This test validates that the Adaptive Playback of 1920x1080 and 960x540 resolution
     * assets of 3 seconds duration each at 30 fps for R perf class,
     * playing alternatively, for at least 30 seconds worth of frames or for 31 seconds of elapsed
     * time, must not drop more than 3 frames for R perf class.
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @CddTest(requirement = "2.2.7.1/5.3/H-1-2")
    public void test30Fps() throws Exception {
        Assume.assumeTrue("Test is limited to R performance class devices or devices that do not " +
                "advertise performance class",
            Utils.isRPerfClass() || !Utils.isPerfClass());
        int frameRate = 30;

        PerformanceClassEvaluator pce = new PerformanceClassEvaluator(this.mTestName);
        PerformanceClassEvaluator.FrameDropRequirement r5_3__H_1_2_R = pce.addR5_3__H_1_2_R();

        int framesDropped = testAdaptivePlaybackFrameDrop(frameRate);

        r5_3__H_1_2_R.setFramesDropped(framesDropped);
        r5_3__H_1_2_R.setFrameRate(frameRate);
        pce.submitAndCheck();
    }

    /**
     * This test validates that the Adaptive Playback of 1920x1080 and 960x540 resolution
     * assets of 3 seconds duration each at 60 fps for S or T perf class,
     * playing alternatively, for at least 30 seconds worth of frames or for 31 seconds of elapsed
     * time, must not drop more than 6 frames for S perf class / 3 frames for T perf class .
     */
    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    @CddTest(requirement = "2.2.7.1/5.3/H-1-2")
    public void test60Fps() throws Exception {
        Assume.assumeTrue("Test is limited to S/T performance class devices or devices that do " +
                "not advertise performance class",
            Utils.isSPerfClass() || Utils.isTPerfClass() || !Utils.isPerfClass());
        int frameRate = 60;

        PerformanceClassEvaluator pce = new PerformanceClassEvaluator(this.mTestName);
        PerformanceClassEvaluator.FrameDropRequirement r5_3__H_1_2_ST = pce.addR5_3__H_1_2_ST();

        int framesDropped = testAdaptivePlaybackFrameDrop(frameRate);

        r5_3__H_1_2_ST.setFramesDropped(framesDropped);
        r5_3__H_1_2_ST.setFrameRate(frameRate);
        pce.submitAndCheck();
    }
}
