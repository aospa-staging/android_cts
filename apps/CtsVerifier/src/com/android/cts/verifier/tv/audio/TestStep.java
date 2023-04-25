/*
 * Copyright (C) 2019 The Android Open Source Project
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
 * limitations under the License
 */
package com.android.cts.verifier.tv.audio;

import android.app.Activity;
import android.view.View;

import androidx.annotation.StringRes;

import com.android.cts.verifier.tv.TestStepBase;
import com.android.cts.verifier.tv.TvAppVerifierActivity;

import java.util.List;

/** Test step containing instruction to the user and a button. */
public abstract class TestStep extends TestStepBase {
    protected View mButtonView;

    @StringRes private int mButtonStringId;

    /**
     * Constructs a test step containing instruction to the user and a button.
     *
     * @param context The test activity which this test step is part of.
     * @param instructionText Text of the test instruction visible to the user.
     * @param buttonStringId Id of a string resource containing the text of the button.
     */
    public TestStep(
            Activity context, String instructionText, @StringRes int buttonStringId) {
        super(context, instructionText);
        mButtonStringId = buttonStringId;
    }

    /** Creates the View for this test step in the context {@link TvAppVerifierActivity}. */
    @Override
    public List<View> createUiElements() {
        List<View> list = super.createUiElements();
        mButtonView =
                TvAppVerifierActivity.createButtonItem(mContext.getLayoutInflater(),
                        null,
                        mButtonStringId,
                        (View view) -> {
                            onButtonClickRunTest();
                        });
        list.add(mButtonView);
        return list;
    }

    @Override
    public void enableInteractivity() {
        TvAppVerifierActivity.setButtonEnabled(mButtonView, true);
    }

    @Override
    public void disableInteractivity() {
        TvAppVerifierActivity.setButtonEnabled(mButtonView, false);
    }


    public abstract void runTest();

    private void onButtonClickRunTest() {
        disableInteractivity();
        runTest();
        done();
    }
}
