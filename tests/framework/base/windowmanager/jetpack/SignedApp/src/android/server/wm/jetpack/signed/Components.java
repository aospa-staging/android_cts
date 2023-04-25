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

package android.server.wm.jetpack.signed;

import android.content.ComponentName;
import android.server.wm.component.ComponentsBase;

public class Components extends ComponentsBase {

    public static final ComponentName SIGNED_EMBEDDING_ACTIVITY =
            component("SignedEmbeddingActivity");

    private static ComponentName component(String className) {
        return component(Components.class, className);
    }
}
