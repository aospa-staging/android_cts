/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.print.test.services;

import android.content.Context;
import android.print.PrinterId;
import android.printservice.PrintJob;
import android.printservice.PrintService;
import android.printservice.PrinterDiscoverySession;
import android.util.Log;

import java.util.List;

public abstract class StubbablePrintService extends PrintService {
    private static final String LOG_TAG = StubbablePrintService.class.getSimpleName();

    @Override
    public PrinterDiscoverySession onCreatePrinterDiscoverySession() {
        PrintServiceCallbacks callbacks = getCallbacks();
        if (callbacks != null) {
            return new StubbablePrinterDiscoverySession(this,
                    getCallbacks().onCreatePrinterDiscoverySessionCallbacks());
        }

        Log.w(LOG_TAG, "onCreatePrinterDiscoverySession called but no callbacks are set up");
        return new PrinterDiscoverySession() {
            @Override
            public void onStartPrinterDiscovery(List<PrinterId> priorityList) {
                // empty
            }

            @Override
            public void onStopPrinterDiscovery() {
                // empty
            }

            @Override
            public void onValidatePrinters(List<PrinterId> printerIds) {
                // empty
            }

            @Override
            public void onStartPrinterStateTracking(PrinterId printerId) {
                // empty
            }

            @Override
            public void onStopPrinterStateTracking(PrinterId printerId) {
                // empty
            }

            @Override
            public void onDestroy() {
                // empty
            }
        };
    }

    @Override
    public void onRequestCancelPrintJob(PrintJob printJob) {
        PrintServiceCallbacks callbacks = getCallbacks();
        if (callbacks != null) {
            callbacks.onRequestCancelPrintJob(printJob);
        }
    }

    @Override
    public void onPrintJobQueued(PrintJob printJob) {
        PrintServiceCallbacks callbacks = getCallbacks();
        if (callbacks != null) {
            callbacks.onPrintJobQueued(printJob);
        }
    }

    protected abstract PrintServiceCallbacks getCallbacks();

    public void callAttachBaseContext(Context base) {
        attachBaseContext(base);
    }

    public List<PrintJob> callGetActivePrintJobs() {
        return getActivePrintJobs();
    }

}
