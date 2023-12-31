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

package android.security.cts;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2021_0484 extends NonRootSecurityTestCase {

    /**
     * b/173720767
     * Vulnerability Behavior: EXIT_VULNERABLE (113)
     * Vulnerable library    : libmedia
     * Is Play managed       : No
     */
    @Test
    @AsbSecurityTest(cveBugId = 173720767)
    public void testPocCVE_2021_0484() throws Exception {
       AdbUtils.runPocAssertExitStatusNotVulnerable("CVE-2021-0484", getDevice(), 300);
    }
}
