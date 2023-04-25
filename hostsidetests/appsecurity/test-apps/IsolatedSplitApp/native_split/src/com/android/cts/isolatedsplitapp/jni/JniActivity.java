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

package com.android.cts.isolatedsplitapp.jni;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class JniActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String errorMessage = "";
        try {
            System.loadLibrary("splitappjni_isolated");
        } catch (UnsatisfiedLinkError e) {
            errorMessage = e.getMessage();
        }

        final Intent resultIntent = new Intent();
        resultIntent.putExtra(Intent.EXTRA_RETURN_RESULT, errorMessage);
        setResult(1, resultIntent);
        finish();
    }
}
