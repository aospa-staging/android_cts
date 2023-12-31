/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.server.wm.app;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_ASSISTANT;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.server.wm.app.Components.AssistantActivity.EXTRA_ASSISTANT_DISPLAY_ID;
import static android.server.wm.app.Components.AssistantActivity.EXTRA_ASSISTANT_ENTER_PIP;
import static android.server.wm.app.Components.AssistantActivity.EXTRA_ASSISTANT_FINISH_SELF;
import static android.server.wm.app.Components.AssistantActivity.EXTRA_ASSISTANT_LAUNCH_NEW_TASK;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class AssistantActivity extends Activity {
    private static final String TAG = "AssistantActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set the layout
        setContentView(R.layout.assistant);

        // Launch the new activity if requested
        if (getIntent().hasExtra(EXTRA_ASSISTANT_LAUNCH_NEW_TASK)) {
            final ComponentName launchActivity = ComponentName.unflattenFromString(
                    getIntent().getStringExtra(EXTRA_ASSISTANT_LAUNCH_NEW_TASK));
            final Intent launchIntent = new Intent();
            launchIntent.setComponent(launchActivity)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            if (getIntent().hasExtra(EXTRA_ASSISTANT_DISPLAY_ID)) {
                ActivityOptions displayOptions = ActivityOptions.makeBasic();
                displayOptions.setLaunchDisplayId(Integer.parseInt(getIntent()
                        .getStringExtra(EXTRA_ASSISTANT_DISPLAY_ID)));
                startActivity(launchIntent, displayOptions.toBundle());
            } else {
                startActivity(launchIntent);
            }
        }

        // Enter pip if requested
        if (getIntent().hasExtra(EXTRA_ASSISTANT_ENTER_PIP)) {
            try {
                enterPictureInPictureMode();
            } catch (IllegalStateException e) {
                finish();
                return;
            }
        }

        // Finish this activity if requested
        if (getIntent().hasExtra(EXTRA_ASSISTANT_FINISH_SELF)) {
            finish();
        }
    }

    /**
     * Launches a new instance of the AssistantActivity directly into the assistant stack.
     */
    static void launchActivityIntoAssistantStack(Activity caller, Bundle extras) {
        final Intent intent = new Intent(caller, AssistantActivity.class);
        intent.setFlags(FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_NEW_TASK);
        if (extras != null) {
            intent.putExtras(extras);
        }

        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchActivityType(ACTIVITY_TYPE_ASSISTANT);
        options.setLaunchWindowingMode(WINDOWING_MODE_FULLSCREEN);
        try {
            // Launching an activity into the assistant stack could fail and cause a security
            // exception. Given that the tests that launch this activity check if this succeeded or
            // not by waiting and asserting the existence of the activity, we shouldn't let the
            // exception kill the process and subsequent unrelated tests fail too.
            caller.startActivity(intent, options.toBundle());
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to launch activity into assistant stack: " + e);
        }
    }
}
