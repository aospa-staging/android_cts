/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.cts.verifier.nfc;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.Ndef;
import android.nfc.tech.TagTechnology;
import android.os.Bundle;

import com.android.cts.verifier.ArrayTestListAdapter;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.TestListAdapter.TestListItem;
import com.android.cts.verifier.nfc.hce.HceEmulatorTestActivity;
import com.android.cts.verifier.nfc.hce.HceReaderTestActivity;
import com.android.cts.verifier.nfc.hcef.HceFEmulatorTestActivity;
import com.android.cts.verifier.nfc.hcef.HceFReaderTestActivity;
import com.android.cts.verifier.nfc.offhost.OffhostUiccEmulatorTestActivity;
import com.android.cts.verifier.nfc.offhost.OffhostUiccReaderTestActivity;

/** Activity that lists all the NFC tests. */
public class NfcTestActivity extends PassFailButtons.TestListActivity {

    private static final String NDEF_ID =
            TagVerifierActivity.getTagTestId(Ndef.class);

    private static final String MIFARE_ULTRALIGHT_ID =
            TagVerifierActivity.getTagTestId(MifareUltralight.class);

    private static final String FEATURE_NFC_MIFARE = "com.nxp.mifare";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pass_fail_list);
        setInfoResources(R.string.nfc_test, R.string.nfc_test_info, 0);
        setPassFailButtonClickListeners();

        ArrayTestListAdapter adapter = new ArrayTestListAdapter(this);

        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC)) {
            adapter.add(TestListItem.newCategory(this, R.string.nfc_tag_verification));
            adapter.add(TestListItem.newTest(this, R.string.nfc_ndef,
                    NDEF_ID, getTagIntent(Ndef.class), null));
            if (getPackageManager().hasSystemFeature(FEATURE_NFC_MIFARE)) {
                adapter.add(TestListItem.newTest(this, R.string.nfc_mifare_ultralight,
                        MIFARE_ULTRALIGHT_ID, getTagIntent(MifareUltralight.class), null));
            }
        }

        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)) {
            adapter.add(TestListItem.newCategory(this, R.string.nfc_hce));
            if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC)) {
                adapter.add(TestListItem.newTest(this, R.string.nfc_hce_reader_tests,
                        HceReaderTestActivity.class.getName(),
                        new Intent(this, HceReaderTestActivity.class), null));
            }
            adapter.add(TestListItem.newTest(this, R.string.nfc_hce_emulator_tests,
                    HceEmulatorTestActivity.class.getName(),
                    new Intent(this, HceEmulatorTestActivity.class), null));
        }

        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION_NFCF)) {
            adapter.add(TestListItem.newCategory(this, R.string.nfc_hce_f));
            if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC)) {
                adapter.add(TestListItem.newTest(this, R.string.nfc_hce_f_reader_tests,
                        HceFReaderTestActivity.class.getName(),
                        new Intent(this, HceFReaderTestActivity.class), null));
            }
            adapter.add(TestListItem.newTest(this, R.string.nfc_hce_f_emulator_tests,
                    HceFEmulatorTestActivity.class.getName(),
                    new Intent(this, HceFEmulatorTestActivity.class), null));
        }

        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC_OFF_HOST_CARD_EMULATION_UICC)) {
            adapter.add(TestListItem.newCategory(this, R.string.nfc_offhost_uicc));
            if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC)) {
                adapter.add(TestListItem.newTest(this, R.string.nfc_offhost_uicc_reader_tests,
                        OffhostUiccReaderTestActivity.class.getName(),
                        new Intent(this, OffhostUiccReaderTestActivity.class), null));
            }
            adapter.add(TestListItem.newTest(this, R.string.nfc_offhost_uicc_emulator_tests,
                    OffhostUiccEmulatorTestActivity.class.getName(),
                    new Intent(this, OffhostUiccEmulatorTestActivity.class), null));
        }

        setTestListAdapter(adapter);
    }

    private Intent getTagIntent(Class<? extends TagTechnology> primaryTech) {
        return new Intent(this, TagVerifierActivity.class)
                .putExtra(TagVerifierActivity.EXTRA_TECH, primaryTech.getName());
    }
}
