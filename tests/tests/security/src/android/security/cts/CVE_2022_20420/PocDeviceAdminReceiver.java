/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.security.cts.CVE_2022_20420;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

public class PocDeviceAdminReceiver extends DeviceAdminReceiver {

    @Override
    public void onEnabled(Context context, Intent intent) {
        try {
            context.sendBroadcast(new Intent("broadcastCVE_2022_20420"));
        } catch (Exception e) {
            // ignore all exceptions.
        }
    }
}
