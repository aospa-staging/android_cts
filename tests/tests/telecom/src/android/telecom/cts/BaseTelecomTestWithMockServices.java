/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.telecom.cts;

import static android.telecom.cts.TestUtils.PACKAGE;
import static android.telecom.cts.TestUtils.TAG;
import static android.telecom.cts.TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;

import android.app.AppOpsManager;
import android.app.UiAutomation;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.RemoteException;
import android.os.Process;
import android.os.UserHandle;
import android.provider.CallLog;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.Conference;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.InCallService;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telecom.cts.MockInCallService.InCallServiceCallbacks;
import android.telecom.cts.carmodetestapp.ICtsCarModeInCallServiceControl;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.telephony.emergency.EmergencyNumber;
import android.test.InstrumentationTestCase;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Base class for Telecom CTS tests that require a {@link CtsConnectionService} and
 * {@link MockInCallService} to verify Telecom functionality.
 */
public class BaseTelecomTestWithMockServices extends InstrumentationTestCase {

    public static final int FLAG_REGISTER = 0x1;
    public static final int FLAG_ENABLE = 0x2;
    public static final int FLAG_SET_DEFAULT = 0x4;
    public static final int FLAG_PHONE_ACCOUNT_HANDLES_CONTENT_SCHEME = 0x8;

    // Don't accidently use emergency number.
    private static int sCounter = 5553638;

    public static final String TEST_EMERGENCY_NUMBER = "5553637";
    public static final Uri TEST_EMERGENCY_URI = Uri.fromParts("tel", TEST_EMERGENCY_NUMBER, null);
    public static final String PKG_NAME = "android.telecom.cts";
    public static final String PERMISSION_PROCESS_OUTGOING_CALLS =
            "android.permission.PROCESS_OUTGOING_CALLS";

    Context mContext;
    TelecomManager mTelecomManager;
    TelephonyManager mTelephonyManager;
    UiModeManager mUiModeManager;

    TestUtils.InvokeCounter mOnBringToForegroundCounter;
    TestUtils.InvokeCounter mOnCallAudioStateChangedCounter;
    TestUtils.InvokeCounter mOnPostDialWaitCounter;
    TestUtils.InvokeCounter mOnCannedTextResponsesLoadedCounter;
    TestUtils.InvokeCounter mOnSilenceRingerCounter;
    TestUtils.InvokeCounter mOnConnectionEventCounter;
    TestUtils.InvokeCounter mOnExtrasChangedCounter;
    TestUtils.InvokeCounter mOnPropertiesChangedCounter;
    TestUtils.InvokeCounter mOnRttModeChangedCounter;
    TestUtils.InvokeCounter mOnRttStatusChangedCounter;
    TestUtils.InvokeCounter mOnRttInitiationFailedCounter;
    TestUtils.InvokeCounter mOnRttRequestCounter;
    TestUtils.InvokeCounter mOnHandoverCompleteCounter;
    TestUtils.InvokeCounter mOnHandoverFailedCounter;
    TestUtils.InvokeCounter mOnPhoneAccountChangedCounter;
    Bundle mPreviousExtras;
    int mPreviousProperties = -1;
    PhoneAccountHandle mPreviousPhoneAccountHandle = null;

    InCallServiceCallbacks mInCallCallbacks;
    String mPreviousDefaultDialer = null;
    PhoneAccountHandle mPreviousDefaultOutgoingAccount = null;
    boolean mShouldRestoreDefaultOutgoingAccount = false;
    MockConnectionService connectionService = null;
    boolean mIsEmergencyCallingSetup = false;

    HandlerThread mTelephonyCallbackThread;
    Handler mTelephonyCallbackHandler;
    TestTelephonyCallback mTelephonyCallback;
    TestCallStateListener mTestCallStateListener;
    Handler mHandler;

    /**
     * Uses the control interface to disable car mode.
     * @param expectedUiMode
     */
    protected void disableAndVerifyCarMode(ICtsCarModeInCallServiceControl control,
            int expectedUiMode) {
        if (control == null) {
            return;
        }
        try {
            control.disableCarMode();
        } catch (RemoteException re) {
            fail("Bee-boop; can't control the incall service");
        }
        assertUiMode(expectedUiMode);
    }

    protected void disconnectAllCallsAndVerify(ICtsCarModeInCallServiceControl controlBinder) {
        if (controlBinder == null) {
            return;
        }
        try {
            controlBinder.disconnectCalls();
        } catch (RemoteException re) {
            fail("Bee-boop; can't control the incall service");
        }
        assertCarModeCallCount(controlBinder, 0);
    }

    /**
     * Verify the car mode ICS has an expected call count.
     * @param expected
     */
    protected void assertCarModeCallCount(ICtsCarModeInCallServiceControl control, int expected) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return expected;
                    }

                    @Override
                    public Object actual() {
                        int callCount = 0;
                        try {
                            callCount = control.getCallCount();
                        } catch (RemoteException re) {
                            fail("Bee-boop; can't control the incall service");
                        }
                        return callCount;
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Expected " + expected + " calls."
        );
    }

    static class TestCallStateListener extends TelephonyCallback
            implements TelephonyCallback.CallStateListener {

        private CountDownLatch mCountDownLatch = new CountDownLatch(1);
        private int mLastState = -1;

        @Override
        public void onCallStateChanged(int state) {
            Log.i(TAG, "onCallStateChanged: state=" + state);
            mLastState = state;
            mCountDownLatch.countDown();
            mCountDownLatch = new CountDownLatch(1);
        }

        public CountDownLatch getCountDownLatch() {
            return mCountDownLatch;
        }

        public int getLastState() {
            return mLastState;
        }
    }

    static class TestTelephonyCallback extends TelephonyCallback implements
            TelephonyCallback.CallStateListener,
            TelephonyCallback.OutgoingEmergencyCallListener,
            TelephonyCallback.EmergencyNumberListListener {
        /** Semaphore released for every callback invocation. */
        public Semaphore mCallbackSemaphore = new Semaphore(0);

        List<Integer> mCallStates = new ArrayList<>();
        EmergencyNumber mLastOutgoingEmergencyNumber;

        LinkedBlockingQueue<Map<Integer, List<EmergencyNumber>>> mEmergencyNumberListQueue =
               new LinkedBlockingQueue<>(2);

        @Override
        public void onCallStateChanged(int state) {
            Log.i(TAG, "onCallStateChanged: state=" + state);
            mCallStates.add(state);
            mCallbackSemaphore.release();
        }

        @Override
        public void onOutgoingEmergencyCall(EmergencyNumber emergencyNumber, int subscriptionId) {
            Log.i(TAG, "onOutgoingEmergencyCall: emergencyNumber=" + emergencyNumber);
            mLastOutgoingEmergencyNumber = emergencyNumber;
            mCallbackSemaphore.release();
        }

        @Override
        public void onEmergencyNumberListChanged(
                Map<Integer, List<EmergencyNumber>> emergencyNumberList) {
            Log.i(TAG, "onEmergencyNumberChanged, total size=" + emergencyNumberList.values()
                    .stream().mapToInt(List::size).sum());
            mEmergencyNumberListQueue.offer(emergencyNumberList);
        }

        public Map<Integer, List<EmergencyNumber>> waitForEmergencyNumberListUpdate(
                long timeoutMillis) throws Throwable {
            return mEmergencyNumberListQueue.poll(timeoutMillis, TimeUnit.MILLISECONDS);
        }
    }

    boolean mShouldTestTelecom = true;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getContext();
        mHandler = new Handler(Looper.getMainLooper());
        mShouldTestTelecom = TestUtils.shouldTestTelecom(mContext);
        if (!mShouldTestTelecom) {
            return;
        }

        // Assume we start in normal mode at the start of all Telecom tests; a failure to leave car
        // mode in any of the tests would cause subsequent test failures.
        // For Watch, UI_MODE shouldn't be normal mode.
        mUiModeManager = mContext.getSystemService(UiModeManager.class);
        TestUtils.executeShellCommand(getInstrumentation(), "telecom reset-car-mode");

        if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)) {
             assertUiMode(Configuration.UI_MODE_TYPE_WATCH);
        } else {
             assertUiMode(Configuration.UI_MODE_TYPE_NORMAL);
        }

        AppOpsManager aom = mContext.getSystemService(AppOpsManager.class);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(aom,
                (appOpsMan) -> appOpsMan.setUidMode(AppOpsManager.OPSTR_PROCESS_OUTGOING_CALLS,
                Process.myUid(), AppOpsManager.MODE_ALLOWED));

        mTelecomManager = (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
        mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);

        mPreviousDefaultDialer = TestUtils.getDefaultDialer(getInstrumentation());
        TestUtils.setDefaultDialer(getInstrumentation(), PACKAGE);
        setupCallbacks();

       // Register a call state listener.
        mTestCallStateListener = new TestCallStateListener();
        CountDownLatch latch = mTestCallStateListener.getCountDownLatch();
        mTelephonyManager.registerTelephonyCallback(r -> r.run(), mTestCallStateListener);
        latch.await(
                TestUtils.WAIT_FOR_PHONE_STATE_LISTENER_REGISTERED_TIMEOUT_S, TimeUnit.SECONDS);
        // Create a new thread for the telephony callback.
        mTelephonyCallbackThread = new HandlerThread("PhoneStateListenerThread");
        mTelephonyCallbackThread.start();
        mTelephonyCallbackHandler = new Handler(mTelephonyCallbackThread.getLooper());

        mTelephonyCallback = new TestTelephonyCallback();
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyManager,
                (tm) -> tm.registerTelephonyCallback(
                        mTelephonyCallbackHandler::post,
                        mTelephonyCallback));
        UiAutomation uiAutomation =
                InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.grantRuntimePermissionAsUser(PKG_NAME, PERMISSION_PROCESS_OUTGOING_CALLS,
                UserHandle.CURRENT);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        if (!mShouldTestTelecom) {
            return;
        }

        mTelephonyManager.unregisterTelephonyCallback(mTestCallStateListener);

        mTelephonyManager.unregisterTelephonyCallback(mTelephonyCallback);
        mTelephonyCallbackThread.quit();

        cleanupCalls();
        if (!TextUtils.isEmpty(mPreviousDefaultDialer)) {
            TestUtils.setDefaultDialer(getInstrumentation(), mPreviousDefaultDialer);
        }
        tearDownConnectionService(TestUtils.TEST_PHONE_ACCOUNT_HANDLE);
        tearDownEmergencyCalling();
        try {
            assertMockInCallServiceUnbound();
        } catch (Throwable t) {
            // If we haven't unbound, that means there's some dirty state in Telecom that needs
            // cleaning up. Forcibly unbind and clean up Telecom state so that we don't have a
            // cascading failure of tests.
            TestUtils.executeShellCommand(getInstrumentation(), "telecom cleanup-stuck-calls");
            throw t;
        }
        UiAutomation uiAutomation =
                InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.revokeRuntimePermissionAsUser(PKG_NAME, PERMISSION_PROCESS_OUTGOING_CALLS,
                UserHandle.CURRENT);
    }

    protected PhoneAccount setupConnectionService(MockConnectionService connectionService,
            int flags) throws Exception {
        Log.i(TAG, "Setting up mock connection service");
        if (connectionService != null) {
            this.connectionService = connectionService;
        } else {
            // Generate a vanilla mock connection service, if not provided.
            this.connectionService = new MockConnectionService();
        }
        CtsConnectionService.setUp(this.connectionService);

        if ((flags & FLAG_REGISTER) != 0) {
            if ((flags & FLAG_PHONE_ACCOUNT_HANDLES_CONTENT_SCHEME) != 0) {
                mTelecomManager.registerPhoneAccount(
                        TestUtils.TEST_PHONE_ACCOUNT_THAT_HANDLES_CONTENT_SCHEME);
            } else {
                mTelecomManager.registerPhoneAccount(TestUtils.TEST_PHONE_ACCOUNT);
            }
        }
        if ((flags & FLAG_ENABLE) != 0) {
            TestUtils.enablePhoneAccount(getInstrumentation(), TestUtils.TEST_PHONE_ACCOUNT_HANDLE);
            // Wait till the adb commands have executed and account is enabled in Telecom database.
            assertPhoneAccountEnabled(TestUtils.TEST_PHONE_ACCOUNT_HANDLE);
        }

        if ((flags & FLAG_SET_DEFAULT) != 0) {
            mPreviousDefaultOutgoingAccount = mTelecomManager.getUserSelectedOutgoingPhoneAccount();
            mShouldRestoreDefaultOutgoingAccount = true;
            TestUtils.setDefaultOutgoingPhoneAccount(getInstrumentation(),
                    TestUtils.TEST_PHONE_ACCOUNT_HANDLE);
            // Wait till the adb commands have executed and the default has changed.
            assertPhoneAccountIsDefault(TestUtils.TEST_PHONE_ACCOUNT_HANDLE);
        }

        return TestUtils.TEST_PHONE_ACCOUNT;
    }

    protected void tearDownConnectionService(PhoneAccountHandle accountHandle) throws Exception {
        Log.i(TAG, "Tearing down mock connection service");
        if (this.connectionService != null) {
            assertNumConnections(this.connectionService, 0);
        }
        mTelecomManager.unregisterPhoneAccount(accountHandle);
        CtsConnectionService.tearDown();
        assertCtsConnectionServiceUnbound();
        if (mShouldRestoreDefaultOutgoingAccount) {
            TestUtils.setDefaultOutgoingPhoneAccount(getInstrumentation(),
                    mPreviousDefaultOutgoingAccount);
        }
        this.connectionService = null;
        mPreviousDefaultOutgoingAccount = null;
        mShouldRestoreDefaultOutgoingAccount = false;
    }

    protected void setupForEmergencyCalling(String testNumber) throws Exception {
        TestUtils.setSystemDialerOverride(getInstrumentation());
        TestUtils.addTestEmergencyNumber(getInstrumentation(), testNumber);
        TestUtils.setTestEmergencyPhoneAccountPackageFilter(getInstrumentation(), mContext);
        // Emergency calls require special capabilities.
        TestUtils.registerEmergencyPhoneAccount(getInstrumentation(),
                TestUtils.TEST_EMERGENCY_PHONE_ACCOUNT_HANDLE,
                TestUtils.ACCOUNT_LABEL + "E", "tel:555-EMER");
        mIsEmergencyCallingSetup = true;
    }

    protected void tearDownEmergencyCalling() throws Exception {
        if (!mIsEmergencyCallingSetup) return;

        TestUtils.clearSystemDialerOverride(getInstrumentation());
        TestUtils.clearTestEmergencyNumbers(getInstrumentation());
        TestUtils.clearTestEmergencyPhoneAccountPackageFilter(getInstrumentation());
        mTelecomManager.unregisterPhoneAccount(TestUtils.TEST_EMERGENCY_PHONE_ACCOUNT_HANDLE);
    }

    protected void startCallTo(Uri address, PhoneAccountHandle accountHandle) {
        final Intent intent = new Intent(Intent.ACTION_CALL, address);
        if (accountHandle != null) {
            intent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, accountHandle);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }

    void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
        }
    }

    private void setupCallbacks() {
        mInCallCallbacks = new InCallServiceCallbacks() {
            @Override
            public void onCallAdded(Call call, int numCalls) {
                Log.i(TAG, "onCallAdded, Call: " + call + ", Num Calls: " + numCalls);
                this.lock.release();
                mPreviousPhoneAccountHandle = call.getDetails().getAccountHandle();
            }
            @Override
            public void onCallRemoved(Call call, int numCalls) {
                Log.i(TAG, "onCallRemoved, Call: " + call + ", Num Calls: " + numCalls);
            }
            @Override
            public void onParentChanged(Call call, Call parent) {
                Log.i(TAG, "onParentChanged, Call: " + call + ", Parent: " + parent);
                this.lock.release();
            }
            @Override
            public void onChildrenChanged(Call call, List<Call> children) {
                Log.i(TAG, "onChildrenChanged, Call: " + call + "Children: " + children);
                this.lock.release();
            }
            @Override
            public void onConferenceableCallsChanged(Call call, List<Call> conferenceableCalls) {
                Log.i(TAG, "onConferenceableCallsChanged, Call: " + call + ", Conferenceables: " +
                        conferenceableCalls);
            }
            @Override
            public void onDetailsChanged(Call call, Call.Details details) {
                Log.i(TAG, "onDetailsChanged, Call: " + call + ", Details: " + details);
                if (!areBundlesEqual(mPreviousExtras, details.getExtras())) {
                    mOnExtrasChangedCounter.invoke(call, details);
                }
                mPreviousExtras = details.getExtras();

                if (mPreviousProperties != details.getCallProperties()) {
                    mOnPropertiesChangedCounter.invoke(call, details);
                    Log.i(TAG, "onDetailsChanged; properties changed from " + Call.Details.propertiesToString(mPreviousProperties) +
                            " to " + Call.Details.propertiesToString(details.getCallProperties()));
                }
                mPreviousProperties = details.getCallProperties();

                if (details.getAccountHandle() != null &&
                        !details.getAccountHandle().equals(mPreviousPhoneAccountHandle)) {
                    mOnPhoneAccountChangedCounter.invoke(call, details.getAccountHandle());
                }
                mPreviousPhoneAccountHandle = details.getAccountHandle();
            }
            @Override
            public void onCallDestroyed(Call call) {
                Log.i(TAG, "onCallDestroyed, Call: " + call);
            }
            @Override
            public void onCallStateChanged(Call call, int newState) {
                Log.i(TAG, "onCallStateChanged, Call: " + call + ", New State: " + newState);
            }
            @Override
            public void onBringToForeground(boolean showDialpad) {
                mOnBringToForegroundCounter.invoke(showDialpad);
            }
            @Override
            public void onCallAudioStateChanged(CallAudioState audioState) {
                Log.i(TAG, "onCallAudioStateChanged, audioState: " + audioState);
                mOnCallAudioStateChangedCounter.invoke(audioState);
            }
            @Override
            public void onPostDialWait(Call call, String remainingPostDialSequence) {
                mOnPostDialWaitCounter.invoke(call, remainingPostDialSequence);
            }
            @Override
            public void onCannedTextResponsesLoaded(Call call, List<String> cannedTextResponses) {
                mOnCannedTextResponsesLoadedCounter.invoke(call, cannedTextResponses);
            }
            @Override
            public void onConnectionEvent(Call call, String event, Bundle extras) {
                mOnConnectionEventCounter.invoke(call, event, extras);
            }

            @Override
            public void onSilenceRinger() {
                Log.i(TAG, "onSilenceRinger");
                mOnSilenceRingerCounter.invoke();
            }

            @Override
            public void onRttModeChanged(Call call, int mode) {
                mOnRttModeChangedCounter.invoke(call, mode);
            }

            @Override
            public void onRttStatusChanged(Call call, boolean enabled, Call.RttCall rttCall) {
                mOnRttStatusChangedCounter.invoke(call, enabled, rttCall);
            }

            @Override
            public void onRttRequest(Call call, int id) {
                mOnRttRequestCounter.invoke(call, id);
            }

            @Override
            public void onRttInitiationFailure(Call call, int reason) {
                mOnRttInitiationFailedCounter.invoke(call, reason);
            }

            @Override
            public void onHandoverComplete(Call call) {
                mOnHandoverCompleteCounter.invoke(call);
            }

            @Override
            public void onHandoverFailed(Call call, int reason) {
                mOnHandoverFailedCounter.invoke(call, reason);
            }
        };

        MockInCallService.setCallbacks(mInCallCallbacks);

        // TODO: If more InvokeCounters are added in the future, consider consolidating them into a
        // single Collection.
        mOnBringToForegroundCounter = new TestUtils.InvokeCounter("OnBringToForeground");
        mOnCallAudioStateChangedCounter = new TestUtils.InvokeCounter("OnCallAudioStateChanged");
        mOnPostDialWaitCounter = new TestUtils.InvokeCounter("OnPostDialWait");
        mOnCannedTextResponsesLoadedCounter = new TestUtils.InvokeCounter("OnCannedTextResponsesLoaded");
        mOnSilenceRingerCounter = new TestUtils.InvokeCounter("OnSilenceRinger");
        mOnConnectionEventCounter = new TestUtils.InvokeCounter("OnConnectionEvent");
        mOnExtrasChangedCounter = new TestUtils.InvokeCounter("OnDetailsChangedCounter");
        mOnPropertiesChangedCounter = new TestUtils.InvokeCounter("OnPropertiesChangedCounter");
        mOnRttModeChangedCounter = new TestUtils.InvokeCounter("mOnRttModeChangedCounter");
        mOnRttStatusChangedCounter = new TestUtils.InvokeCounter("mOnRttStatusChangedCounter");
        mOnRttInitiationFailedCounter =
                new TestUtils.InvokeCounter("mOnRttInitiationFailedCounter");
        mOnRttRequestCounter = new TestUtils.InvokeCounter("mOnRttRequestCounter");
        mOnHandoverCompleteCounter = new TestUtils.InvokeCounter("mOnHandoverCompleteCounter");
        mOnHandoverFailedCounter = new TestUtils.InvokeCounter("mOnHandoverFailedCounter");
        mOnPhoneAccountChangedCounter = new TestUtils.InvokeCounter(
                "mOnPhoneAccountChangedCounter");
    }

    void addAndVerifyNewFailedIncomingCall(Uri incomingHandle, Bundle extras) {
        assertEquals("Lock should have no permits!", 0, mInCallCallbacks.lock.availablePermits());
        int currentCallCount = 0;
        if (mInCallCallbacks.getService() != null) {
            currentCallCount = mInCallCallbacks.getService().getCallCount();
        }

        if (extras == null) {
            extras = new Bundle();
        }
        extras.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, incomingHandle);
        mTelecomManager.addNewIncomingCall(TestUtils.TEST_PHONE_ACCOUNT_HANDLE, extras);

        if (!connectionService.waitForEvent(
                MockConnectionService.EVENT_CONNECTION_SERVICE_CREATE_CONNECTION_FAILED)) {
            fail("Incoming Connection failure indication did not get called.");
        }

        assertEquals("ConnectionService did not receive failed connection",
                1, connectionService.failedConnections.size());

        assertEquals("Address is not correct for failed connection",
                connectionService.failedConnections.get(0).getAddress(), incomingHandle);

        assertEquals("InCallService should contain the same number of calls.",
                currentCallCount,
                mInCallCallbacks.getService().getCallCount());
    }

    /**
     * Puts Telecom in a state where there is an incoming call provided by the
     * {@link CtsConnectionService} which can be tested.
     */
    void addAndVerifyNewIncomingCall(Uri incomingHandle, Bundle extras) {
        int currentCallCount = addNewIncomingCall(incomingHandle, extras);
        verifyNewIncomingCall(currentCallCount);
    }

    int addNewIncomingCall(Uri incomingHandle, Bundle extras) {
        assertEquals("Lock should have no permits!", 0, mInCallCallbacks.lock.availablePermits());
        int currentCallCount = 0;
        if (mInCallCallbacks.getService() != null) {
            currentCallCount = mInCallCallbacks.getService().getCallCount();
        }

        if (extras == null) {
            extras = new Bundle();
        }
        extras.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, incomingHandle);
        mTelecomManager.addNewIncomingCall(TestUtils.TEST_PHONE_ACCOUNT_HANDLE, extras);

        return currentCallCount;
    }

    void verifyNewIncomingCall(int currentCallCount) {
        try {
            if (!mInCallCallbacks.lock.tryAcquire(TestUtils.WAIT_FOR_CALL_ADDED_TIMEOUT_S,
                    TimeUnit.SECONDS)) {
                fail("No call added to InCallService.");
            }
        } catch (InterruptedException e) {
            Log.i(TAG, "Test interrupted!");
        }

        assertEquals("InCallService should contain 1 more call after adding a call.",
                currentCallCount + 1,
                mInCallCallbacks.getService().getCallCount());
    }

    /**
     *  Puts Telecom in a state where there is an active call provided by the
     *  {@link CtsConnectionService} which can be tested.
     */
    void placeAndVerifyCall() {
        placeAndVerifyCall(null);
    }

    void placeAndVerifyCallByRedirection(boolean wasCancelled) {
        placeAndVerifyCallByRedirection(null, wasCancelled);
    }

    /**
     *  Puts Telecom in a state where there is an active call provided by the
     *  {@link CtsConnectionService} which can be tested.
     */
    void placeAndVerifyCallByRedirection(Bundle extras, boolean wasCancelled) {
        int currentCallCount = (getInCallService() == null) ? 0 : getInCallService().getCallCount();
        int currentConnections = getNumberOfConnections();
        // We expect a new connection if it wasn't cancelled.
        if (!wasCancelled) {
            currentConnections++;
            currentCallCount++;
        }
        placeAndVerifyCall(extras, VideoProfile.STATE_AUDIO_ONLY, currentCallCount);
        // The connectionService.lock is released in
        // MockConnectionService#onCreateOutgoingConnection, however the connection will not
        // actually be added to the list of connections in the ConnectionService until shortly
        // afterwards.  So there is still a potential for the lock to be released before it would
        // be seen by calls to ConnectionService#getAllConnections().
        // We will wait here until the list of connections includes one more connection to ensure
        // that placing the call has fully completed.
        assertCSConnections(currentConnections);

        // Ensure the new outgoing call broadcast fired for the outgoing call.
        assertOutgoingCallBroadcastReceived(true);

        // CTS test does not have read call log permission so should not get the phone number.
        assertNull(NewOutgoingCallBroadcastReceiver.getReceivedNumber());
    }

    /**
     *  Puts Telecom in a state where there is an active call provided by the
     *  {@link CtsConnectionService} which can be tested.
     *
     *  @param videoState the video state of the call.
     */
    void placeAndVerifyCall(int videoState) {
        placeAndVerifyCall(null, videoState);
    }

    /**
     *  Puts Telecom in a state where there is an active call provided by the
     *  {@link CtsConnectionService} which can be tested.
     */
    void placeAndVerifyCall(Bundle extras) {
        placeAndVerifyCall(extras, VideoProfile.STATE_AUDIO_ONLY);
    }

    /**
     *  Puts Telecom in a state where there is an active call provided by the
     *  {@link CtsConnectionService} which can be tested.
     */
    void placeAndVerifyCall(Bundle extras, int videoState) {
        int currentCallCount = (getInCallService() == null) ? 0 : getInCallService().getCallCount();
        // We expect placing the call adds a new call/connection.
        int expectedConnections = getNumberOfConnections() + 1;
        placeAndVerifyCall(extras, videoState, currentCallCount + 1);
        // The connectionService.lock is released in
        // MockConnectionService#onCreateOutgoingConnection, however the connection will not
        // actually be added to the list of connections in the ConnectionService until shortly
        // afterwards.  So there is still a potential for the lock to be released before it would
        // be seen by calls to ConnectionService#getAllConnections().
        // We will wait here until the list of connections includes one more connection to ensure
        // that placing the call has fully completed.
        assertCSConnections(expectedConnections);
        assertOutgoingCallBroadcastReceived(true);

        // CTS test does not have read call log permission so should not get the phone number.
        assertNull(NewOutgoingCallBroadcastReceiver.getReceivedNumber());
    }

    /**
     *  Verifies that a call was not placed
     */
    void placeAndVerifyNoCall(Bundle extras) {
        assertEquals("Lock should have no permits!", 0, mInCallCallbacks.lock.availablePermits());
        placeNewCallWithPhoneAccount(extras, 0);

        try {
            if (!mInCallCallbacks.lock.tryAcquire(TestUtils.WAIT_FOR_CALL_ADDED_TIMEOUT_S,
                    TimeUnit.SECONDS)) {
            }
        } catch (InterruptedException e) {
            Log.i(TAG, "Test interrupted!");
        }

        // Make sure any procedures to disconnect existing calls (makeRoomForOutgoingCall)
        // complete successfully
        TestUtils.waitOnLocalMainLooper(WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        TestUtils.waitOnAllHandlers(getInstrumentation());

        assertNull("Service should be null since call should not have been placed",
                mInCallCallbacks.getService());
    }
    /**
     *  Puts Telecom in a state where there is an active call provided by the
     *  {@link CtsConnectionService} which can be tested.
     */
    void placeAndVerifyCall(Bundle extras, int videoState, int expectedCallCount) {
        assertEquals("Lock should have no permits!", 0, mInCallCallbacks.lock.availablePermits());
        placeNewCallWithPhoneAccount(extras, videoState);

        try {
            if (!mInCallCallbacks.lock.tryAcquire(TestUtils.WAIT_FOR_CALL_ADDED_TIMEOUT_S,
                        TimeUnit.SECONDS)) {
                fail("No call added to InCallService.");
            }
        } catch (InterruptedException e) {
            Log.i(TAG, "Test interrupted!");
        }

        // Make sure any procedures to disconnect existing calls (makeRoomForOutgoingCall)
        // complete successfully
        TestUtils.waitOnLocalMainLooper(WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        TestUtils.waitOnAllHandlers(getInstrumentation());

        assertEquals("InCallService should match the expected count.", expectedCallCount,
                mInCallCallbacks.getService().getCallCount());
    }

    /**
     * Place an emergency call and verify that it has been setup properly.
     *
     * @param supportsHold If telecom supports holding emergency calls, this will expect two
     * calls. If telecom does not support holding emergency calls, this will expect only the
     * emergency call to be active.
     * @return The emergency connection
     */
    public Connection placeAndVerifyEmergencyCall(boolean supportsHold) {
        Bundle extras = new Bundle();
        extras.putParcelable(TestUtils.EXTRA_PHONE_NUMBER, TEST_EMERGENCY_URI);
        // We want to request the active connections vs number of connections because in some cases,
        // we wait to destroy the underlying connection to prevent race conditions. This will result
        // in Connections in the DISCONNECTED state.
        int currentConnectionCount = supportsHold ?
                getNumberOfActiveConnections() + 1 : getNumberOfActiveConnections();
        int currentCallCount = (getInCallService() == null) ? 0 : getInCallService().getCallCount();
        currentCallCount = supportsHold ? currentCallCount + 1 : currentCallCount;
        // The device only supports a max of two calls active at any one time
        currentCallCount = Math.min(currentCallCount, 2);

        placeAndVerifyCall(extras, VideoProfile.STATE_AUDIO_ONLY, currentCallCount);
        // The connectionService.lock is released in
        // MockConnectionService#onCreateOutgoingConnection, however the connection will not
        // actually be added to the list of connections in the ConnectionService until shortly
        // afterwards.  So there is still a potential for the lock to be released before it would
        // be seen by calls to ConnectionService#getAllConnections().
        // We will wait here until the list of connections includes one more connection to ensure
        // that placing the call has fully completed.
        assertActiveCSConnections(currentConnectionCount);

        assertOutgoingCallBroadcastReceived(true);
        Connection connection = verifyConnectionForOutgoingCall(TEST_EMERGENCY_URI);
        TestUtils.waitOnAllHandlers(getInstrumentation());
        return connection;
    }

    int getNumberOfConnections() {
        return CtsConnectionService.getAllConnectionsFromTelecom().size();
    }

    int getNumberOfActiveConnections() {
        return CtsConnectionService.getAllConnectionsFromTelecom().stream()
                .filter(c -> c.getState() != Connection.STATE_DISCONNECTED).collect(
                        Collectors.toSet()).size();
    }

    Connection getConnection(Uri address) {
        return CtsConnectionService.getAllConnectionsFromTelecom().stream()
                .filter(c -> c.getAddress().equals(address)).findFirst().orElse(null);
    }

    MockConnection verifyConnectionForOutgoingCall() {
        // Assuming only 1 connection present
        return verifyConnectionForOutgoingCall(0);
    }

    MockConnection verifyConnectionForOutgoingCall(int connectionIndex) {
        try {
            if (!connectionService.lock.tryAcquire(TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS)) {
                fail("No outgoing call connection requested by Telecom");
            }
        } catch (InterruptedException e) {
            Log.i(TAG, "Test interrupted!");
        }

        assertThat("Telecom should create outgoing connection for outgoing call",
                connectionService.outgoingConnections.size(), not(equalTo(0)));
        MockConnection connection = connectionService.outgoingConnections.get(connectionIndex);
        return connection;
    }

    MockConnection verifyConnectionForOutgoingCall(Uri address) {
        if (!connectionService.waitForEvent(
                MockConnectionService.EVENT_CONNECTION_SERVICE_CREATE_CONNECTION)) {
            fail("No outgoing call connection requested by Telecom");
        }
        assertThat("Telecom should create outgoing connection for outgoing call",
                connectionService.outgoingConnections.size(), not(equalTo(0)));

        // There is a subtle race condition in ConnectionService.  When onCreateIncomingConnection
        // or onCreateOutgoingConnection completes, ConnectionService then adds the connection to
        // the list of tracked connections.  It's very possible for the lock to be released and
        // the connection to have not yet been added to the connection list yet.
        waitUntilConditionIsTrueOrTimeout(new Condition() {
                                              @Override
                                              public Object expected() {
                                                  return true;
                                              }

                                              @Override
                                              public Object actual() {
                                                  return getConnection(address) != null;
                                              }
                                          },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Expected call from number " + address);
        Connection connection = getConnection(address);

        if (connection instanceof MockConnection) {
            if (connectionService.outgoingConnections.contains(connection)) {
                return (MockConnection) connection;
            }
        }
        return null;
    }

    void verifyNoConnectionForOutgoingCall() {
        try {
            if (!connectionService.lock.tryAcquire(TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS)) {
                //fail("No outgoing call connection requested by Telecom");
            }
        } catch (InterruptedException e) {
            Log.i(TAG, "Test interrupted!");
        }

        assertThat("Telecom should not create outgoing connection for outgoing call",
                connectionService.outgoingConnections.size(), equalTo(0));
        return;
    }

    MockConnection verifyConnectionForIncomingCall() {
        // Assuming only 1 connection present
        return verifyConnectionForIncomingCall(0);
    }

    MockConnection verifyConnectionForIncomingCall(int connectionIndex) {
        try {
            if (!connectionService.lock.tryAcquire(TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS)) {
                fail("No outgoing call connection requested by Telecom");
            }
        } catch (InterruptedException e) {
            Log.i(TAG, "Test interrupted!");
        }

        assertThat("Telecom should create incoming connections for incoming calls",
                connectionService.incomingConnections.size(), not(equalTo(0)));
        MockConnection connection = connectionService.incomingConnections.get(connectionIndex);
        setAndVerifyConnectionForIncomingCall(connection);
        return connection;
    }

    MockConference verifyConference(int permit) {
        try {
            if (!connectionService.lock.tryAcquire(permit, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS)) {
                fail("No conference requested by Telecom");
            }
        } catch (InterruptedException e) {
            Log.i(TAG, "Test interrupted!");
        }
        return connectionService.conferences.get(0);
    }

    void setAndVerifyConnectionForIncomingCall(MockConnection connection) {
        if (connection.getState() == Connection.STATE_ACTIVE) {
            // If the connection is already active (like if it got picked up immediately), don't
            // bother with setting it back to ringing.
            return;
        }
        connection.setRinging();
        assertConnectionState(connection, Connection.STATE_RINGING);
    }

    void setAndVerifyConferenceablesForOutgoingConnection(int connectionIndex) {
        assertEquals("Lock should have no permits!", 0, mInCallCallbacks.lock.availablePermits());
        // Make all other outgoing connections as conferenceable with this connection.
        MockConnection connection = connectionService.outgoingConnections.get(connectionIndex);
        List<Connection> confConnections =
                new ArrayList<>(connectionService.outgoingConnections.size());
        for (Connection c : connectionService.outgoingConnections) {
            if (c != connection) {
                confConnections.add(c);
            }
        }
        connection.setConferenceableConnections(confConnections);
        assertEquals(connection.getConferenceables(), confConnections);
    }

    void addConferenceCall(Call call1, Call call2) {
        assertEquals("Lock should have no permits!", 0, mInCallCallbacks.lock.availablePermits());
        int currentConfCallCount = 0;
        if (mInCallCallbacks.getService() != null) {
            currentConfCallCount = mInCallCallbacks.getService().getConferenceCallCount();
        }
        // Verify that the calls have each other on their conferenceable list before proceeding
        List<Call> callConfList = new ArrayList<>();
        callConfList.add(call2);
        assertCallConferenceableList(call1, callConfList);

        callConfList.clear();
        callConfList.add(call1);
        assertCallConferenceableList(call2, callConfList);

        call1.conference(call2);

        /**
         * We should have 1 onCallAdded, 2 onChildrenChanged and 2 onParentChanged invoked, so
         * we should have 5 available permits on the incallService lock.
         */
        try {
            if (!mInCallCallbacks.lock.tryAcquire(5, 3, TimeUnit.SECONDS)) {
                fail("Conference addition failed.");
            }
        } catch (InterruptedException e) {
            Log.i(TAG, "Test interrupted!");
        }

        assertEquals("InCallService should contain 1 more call after adding a conf call.",
                currentConfCallCount + 1,
                mInCallCallbacks.getService().getConferenceCallCount());
    }

    void splitFromConferenceCall(Call call1) {
        assertEquals("Lock should have no permits!", 0, mInCallCallbacks.lock.availablePermits());

        call1.splitFromConference();
        /**
         * We should have 1 onChildrenChanged and 1 onParentChanged invoked, so
         * we should have 2 available permits on the incallService lock.
         */
        try {
            if (!mInCallCallbacks.lock.tryAcquire(2, 3, TimeUnit.SECONDS)) {
                fail("Conference split failed");
            }
        } catch (InterruptedException e) {
            Log.i(TAG, "Test interrupted!");
        }
    }

    MockConference verifyConferenceForOutgoingCall() {
        try {
            if (!connectionService.lock.tryAcquire(TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS)) {
                fail("No outgoing conference requested by Telecom");
            }
        } catch (InterruptedException e) {
            Log.i(TAG, "Test interrupted!");
        }
        // Return the newly created conference object to the caller
        MockConference conference = connectionService.conferences.get(0);
        setAndVerifyConferenceForOutgoingCall(conference);
        return conference;
    }

    Pair<Conference, ConnectionRequest> verifyAdhocConferenceCall() {
        try {
            if (!connectionService.lock.tryAcquire(2, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS)) {
                fail("No conference requested by Telecom");
            }
        } catch (InterruptedException e) {
            Log.i(TAG, "Test interrupted!");
        }
        return new Pair<>(connectionService.conferences.get(0),
                connectionService.connectionRequest);
    }

    void setAndVerifyConferenceForOutgoingCall(MockConference conference) {
        conference.setActive();
        assertConferenceState(conference, Connection.STATE_ACTIVE);
    }

    void verifyCallStateListener(int expectedCallState) throws InterruptedException {
        mTestCallStateListener.getCountDownLatch().await(
                TestUtils.WAIT_FOR_PHONE_STATE_LISTENER_CALLBACK_TIMEOUT_S, TimeUnit.SECONDS);
        assertEquals(expectedCallState, mTestCallStateListener.getLastState());
    }

    void verifyPhoneStateListenerCallbacksForCall(int expectedCallState, String expectedNumber)
            throws Exception {
        assertTrue(mTelephonyCallback.mCallbackSemaphore.tryAcquire(
                TestUtils.WAIT_FOR_PHONE_STATE_LISTENER_CALLBACK_TIMEOUT_S, TimeUnit.SECONDS));
        // At this point we can only be sure that we got AN update, but not necessarily the one we
        // are looking for; wait until we see the state we want before verifying further.
        waitUntilConditionIsTrueOrTimeout(new Condition() {
                                              @Override
                                              public Object expected() {
                                                  return true;
                                              }

                                              @Override
                                              public Object actual() {
                                                  return mTelephonyCallback.mCallStates
                                                          .stream()
                                                          .filter(p -> p == expectedCallState)
                                                          .count() > 0;
                                              }
                                          },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Expected call state " + expectedCallState + " and number "
                        + expectedNumber);


        // Get the most recent callback; it is possible that there was an initial state reported due
        // to the fact that TelephonyManager will sometimes give an initial state back to the caller
        // when the listener is registered.
        int callState = mTelephonyCallback.mCallStates.get(
                mTelephonyCallback.mCallStates.size() - 1);
        assertEquals(expectedCallState, callState);
        // Note: We do NOT check the phone number here.  Due to changes in how the phone state
        // broadcast is sent, the caller may receive multiple broadcasts, and the number will be
        // present in one or the other.  We waited for a full matching broadcast above so we can
        // be sure the number was reported as expected.
    }

    void verifyPhoneStateListenerCallbacksForEmergencyCall(String expectedNumber)
        throws Exception {
        assertTrue(mTelephonyCallback.mCallbackSemaphore.tryAcquire(
            TestUtils.WAIT_FOR_PHONE_STATE_LISTENER_CALLBACK_TIMEOUT_S, TimeUnit.SECONDS));
        // At this point we can only be sure that we got AN update, but not necessarily the one we
        // are looking for; wait until we see the state we want before verifying further.
        waitUntilConditionIsTrueOrTimeout(new Condition() {
                                              @Override
                                              public Object expected() {
                                                  return true;
                                              }

                                              @Override
                                              public Object actual() {
                                                  return mTelephonyCallback
                                                      .mLastOutgoingEmergencyNumber != null
                                                      && mTelephonyCallback
                                                      .mLastOutgoingEmergencyNumber.getNumber()
                                                      .equals(expectedNumber);
                                              }
                                          },
            WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
            "Expected emergency number: " + expectedNumber);

        assertEquals(mTelephonyCallback.mLastOutgoingEmergencyNumber.getNumber(),
            expectedNumber);
    }

    /**
     * Disconnect the created test call and verify that Telecom has cleared all calls.
     */
    void cleanupCalls() {
        if (mInCallCallbacks != null && mInCallCallbacks.getService() != null) {
            mInCallCallbacks.getService().disconnectAllConferenceCalls();
            mInCallCallbacks.getService().disconnectAllCalls();
            assertNumConferenceCalls(mInCallCallbacks.getService(), 0);
            assertNumCalls(mInCallCallbacks.getService(), 0);
        }
    }

    /**
     * Place a new outgoing call via the {@link CtsConnectionService}
     */
    private void placeNewCallWithPhoneAccount(Bundle extras, int videoState) {
        if (extras == null) {
            extras = new Bundle();
        }
        if (!extras.containsKey(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE)) {
            extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,
                    TestUtils.TEST_PHONE_ACCOUNT_HANDLE);
        }

        if (!VideoProfile.isAudioOnly(videoState)) {
            extras.putInt(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, videoState);
        }
        Uri number;
        if (extras.containsKey(TestUtils.EXTRA_PHONE_NUMBER)) {
            number = extras.getParcelable(TestUtils.EXTRA_PHONE_NUMBER);
        } else {
            number = createTestNumber();
        }
        mTelecomManager.placeCall(number, extras);
    }

    /**
     * Create a new number each time for a new test. Telecom has special logic to reuse certain
     * calls if multiple calls to the same number are placed within a short period of time which
     * can cause certain tests to fail.
     */
    Uri createTestNumber() {
        return Uri.fromParts("tel", String.valueOf(++sCounter), null);
    }

    /**
     * Creates a new random phone number in the range:
     * 000-000-0000
     * to
     * 999-999-9999
     * @return Randomized phone number.
     */
    Uri createRandomTestNumber() {
        return Uri.fromParts("tel", String.format("16%05d", new Random().nextInt(99999))
                + String.format("%04d", new Random().nextInt(9999)), null);
    }

    public static Uri getTestNumber() {
        return Uri.fromParts("tel", String.valueOf(sCounter), null);
    }

    public boolean isLoggedCall(PhoneAccountHandle handle) {
        PhoneAccount phoneAccount = mTelecomManager.getPhoneAccount(handle);
        Bundle extras = phoneAccount.getExtras();
        if (extras == null) {
            extras = new Bundle();
        }
        boolean isSelfManaged = (phoneAccount.getCapabilities()
                & PhoneAccount.CAPABILITY_SELF_MANAGED) == PhoneAccount.CAPABILITY_SELF_MANAGED;
        // Calls are logged if:
        // 1. They're not self-managed
        // 2. They're self-managed and are configured to request logging.
        return (!isSelfManaged
                || (isSelfManaged
                && extras.getBoolean(PhoneAccount.EXTRA_LOG_SELF_MANAGED_CALLS)
                && (phoneAccount.getSupportedUriSchemes().contains(PhoneAccount.SCHEME_TEL)
                || phoneAccount.getSupportedUriSchemes().contains(PhoneAccount.SCHEME_SIP))));
    }

    public CountDownLatch getCallLogEntryLatch() {
        CountDownLatch changeLatch = new CountDownLatch(1);
        mContext.getContentResolver().registerContentObserver(
                CallLog.Calls.CONTENT_URI, true,
                new ContentObserver(mHandler) {
                    @Override
                    public void onChange(boolean selfChange, Uri uri) {
                        mContext.getContentResolver().unregisterContentObserver(this);
                        changeLatch.countDown();
                        super.onChange(selfChange);
                    }
                });
        return changeLatch;
    }


    public void verifyCallLogging(CountDownLatch logLatch, boolean isCallLogged, Uri testNumber) {
        Cursor logCursor = getLatestCallLogCursorIfMatchesUri(logLatch, isCallLogged, testNumber);
        if (isCallLogged) {
            assertNotNull("Call log entry not found for test number", logCursor);
        }
    }

    public void verifyCallLogging(Uri testNumber, int expectedLogType) {
        CountDownLatch logLatch = getCallLogEntryLatch();
        Cursor logCursor = getLatestCallLogCursorIfMatchesUri(logLatch, true /*isCallLogged*/,
                testNumber);
        assertNotNull("Call log entry not found for test number", logCursor);
        int typeIndex = logCursor.getColumnIndex(CallLog.Calls.TYPE);
        int type = logCursor.getInt(typeIndex);
        assertEquals("recorded type does not match expected", expectedLogType, type);
    }

    public Cursor getLatestCallLogCursorIfMatchesUri(CountDownLatch latch, boolean newLogExpected,
            Uri testNumber) {
        if (newLogExpected) {
            // Wait for the content observer to report that we have gotten a new call log entry.
            try {
                latch.await(WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ie) {
                fail("Expected log latch");
            }
        }

        // Query the latest entry into the call log.
        Cursor callsCursor = mContext.getContentResolver().query(CallLog.Calls.CONTENT_URI, null,
                null, null, CallLog.Calls._ID + " DESC limit 1;");
        int numberIndex = callsCursor.getColumnIndex(CallLog.Calls.NUMBER);
        if (callsCursor.moveToNext()) {
            String number = callsCursor.getString(numberIndex);
            if (testNumber.getSchemeSpecificPart().equals(number)) {
                return callsCursor;
            } else {
                // Last call log entry doesnt match expected number.
                return null;
            }
        }
        // No Calls
        return null;
    }

    void assertNumCalls(final MockInCallService inCallService, final int numCalls) {
        waitUntilConditionIsTrueOrTimeout(new Condition() {
            @Override
            public Object expected() {
                return numCalls;
            }
            @Override
            public Object actual() {
                return inCallService.getCallCount();
            }
        },
        WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
        "InCallService should contain " + numCalls + " calls."
    );
    }

    void assertNumConferenceCalls(final MockInCallService inCallService, final int numCalls) {
        waitUntilConditionIsTrueOrTimeout(new Condition() {
            @Override
            public Object expected() {
                return numCalls;
            }
            @Override
            public Object actual() {
                return inCallService.getConferenceCallCount();
            }
        },
        WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
        "InCallService should contain " + numCalls + " conference calls."
    );
    }

    void assertActiveCSConnections(final int numConnections) {
        waitUntilConditionIsTrueOrTimeout(new Condition() {
                                              @Override
                                              public Object expected() {
                                                  return numConnections;
                                              }

                                              @Override
                                              public Object actual() {
                                                  return getNumberOfActiveConnections();
                                              }
                                          },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "ConnectionService should contain " + numConnections + " connections."
        );
    }

    void assertCSConnections(final int numConnections) {
        waitUntilConditionIsTrueOrTimeout(new Condition() {
                                              @Override
                                              public Object expected() {
                                                  return numConnections;
                                              }

                                              @Override
                                              public Object actual() {
                                                  return CtsConnectionService
                                                          .getAllConnectionsFromTelecom()
                                                          .size();
                                              }
                                          },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "ConnectionService should contain " + numConnections + " connections."
        );
    }

    void assertNumConnections(final MockConnectionService connService, final int numConnections) {
        waitUntilConditionIsTrueOrTimeout(new Condition() {
                                              @Override
                                              public Object expected() {
                                                  return numConnections;
                                              }
                                              @Override
                                              public Object actual() {
                                                  return connService.getAllConnections().size();
                                              }
                                          },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "ConnectionService should contain " + numConnections + " connections."
        );
    }

    void assertMuteState(final InCallService incallService, final boolean isMuted) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return isMuted;
                    }

                    @Override
                    public Object actual() {
                        final CallAudioState state = incallService.getCallAudioState();
                        return state == null ? null : state.isMuted();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Phone's mute state should be: " + isMuted
        );
    }

    void assertMuteState(final MockConnection connection, final boolean isMuted) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return isMuted;
                    }

                    @Override
                    public Object actual() {
                        final CallAudioState state = connection.getCallAudioState();
                        return state == null ? null : state.isMuted();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Connection's mute state should be: " + isMuted
        );
    }

    void assertAudioRoute(final InCallService incallService, final int route) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return route;
                    }

                    @Override
                    public Object actual() {
                        final CallAudioState state = incallService.getCallAudioState();
                        return state == null ? null : state.getRoute();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Phone's audio route should be: " + route
        );
    }

    void assertNotAudioRoute(final InCallService incallService, final int route) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return new Boolean(true);
                    }

                    @Override
                    public Object actual() {
                        final CallAudioState state = incallService.getCallAudioState();
                        return route != state.getRoute();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Phone's audio route should not be: " + route
        );
    }

    void assertAudioRoute(final MockConnection connection, final int route) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return route;
                    }

                    @Override
                    public Object actual() {
                        final CallAudioState state = ((Connection) connection).getCallAudioState();
                        return state == null ? null : state.getRoute();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Connection's audio route should be: " + route
        );
    }

    void assertConnectionState(final Connection connection, final int state) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return state;
                    }

                    @Override
                    public Object actual() {
                        return connection.getState();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Connection should be in state " + state
        );
    }

    void assertCallState(final Call call, final int state) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return call.getState() == state && call.getDetails().getState() == state;
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Expected state: " + state + ", callState=" + call.getState() + ", detailState="
                    + call.getDetails().getState()
        );
    }

    void assertCallConferenceableList(final Call call, final List<Call> conferenceableList) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return conferenceableList;
                    }

                    @Override
                    public Object actual() {
                        return call.getConferenceableCalls();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Call: " + call + " does not have the correct conferenceable call list."
        );
    }

    void assertDtmfString(final MockConnection connection, final String dtmfString) {
        waitUntilConditionIsTrueOrTimeout(new Condition() {
                @Override
                public Object expected() {
                    return dtmfString;
                }

                @Override
                public Object actual() {
                    return connection.getDtmfString();
                }
            },
            WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
            "DTMF string should be equivalent to entered DTMF characters: " + dtmfString
        );
    }

    void assertDtmfString(final MockConference conference, final String dtmfString) {
        waitUntilConditionIsTrueOrTimeout(new Condition() {
                @Override
                public Object expected() {
                    return dtmfString;
                }

                @Override
                public Object actual() {
                    return conference.getDtmfString();
                }
            },
            WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
            "DTMF string should be equivalent to entered DTMF characters: " + dtmfString
        );
    }

    void assertCallDisplayName(final Call call, final String name) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return name;
                    }

                    @Override
                    public Object actual() {
                        return call.getDetails().getCallerDisplayName();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Call should have display name: " + name
        );
    }

    void assertCallHandle(final Call call, final Uri expectedHandle) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return expectedHandle;
                    }

                    @Override
                    public Object actual() {
                        return call.getDetails().getHandle();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Call should have handle name: " + expectedHandle
        );
    }

    void assertCallConnectTimeChanged(final Call call, final long time) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return call.getDetails().getConnectTimeMillis() != time;
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Call have connect time: " + time
        );
    }

    void assertConnectionCallDisplayName(final Connection connection, final String name) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return name;
                    }

                    @Override
                    public Object actual() {
                        return connection.getCallerDisplayName();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Connection should have display name: " + name
        );
    }

    void assertDisconnectReason(final Connection connection, final String disconnectReason) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return disconnectReason;
                    }

                    @Override
                    public Object actual() {
                        return connection.getDisconnectCause().getReason();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Connection should have been disconnected with reason: " + disconnectReason
        );
    }

    void assertConferenceState(final Conference conference, final int state) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return state;
                    }

                    @Override
                    public Object actual() {
                        return conference.getState();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Conference should be in state " + state
        );
    }


    void assertOutgoingCallBroadcastReceived(boolean received) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return received;
                    }

                    @Override
                    public Object actual() {
                        return NewOutgoingCallBroadcastReceiver
                                .isNewOutgoingCallBroadcastReceived();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                received ? "Outgoing Call Broadcast should be received"
                        : "Outgoing Call Broadcast should not be received"
        );
    }

    void assertCallDetailsConstructed(Call mCall, boolean constructed) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return constructed;
                    }

                    @Override
                    public Object actual() {
                        return mCall != null && mCall.getDetails() != null;
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                constructed ? "Call Details should be constructed"
                        : "Call Details should not be constructed"
        );
    }

    void assertCallGatewayConstructed(Call mCall, boolean constructed) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return constructed;
                    }

                    @Override
                    public Object actual() {
                        return mCall != null && mCall.getDetails() != null
                                && mCall.getDetails().getGatewayInfo() != null;
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                constructed ? "Call Gateway should be constructed"
                        : "Call Gateway should not be constructed"
        );
    }

    void assertCallNotNull(Call mCall, boolean notNull) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return notNull;
                    }

                    @Override
                    public Object actual() {
                        return mCall != null;
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                notNull ? "Call should not be null" : "Call should be null"
        );
    }

    /**
     * Checks all fields of two PhoneAccounts for equality, with the exception of the enabled state.
     * Should only be called after assertPhoneAccountRegistered when it can be guaranteed
     * that the PhoneAccount is registered.
     * @param expected The expected PhoneAccount.
     * @param actual The actual PhoneAccount.
     */
    void assertPhoneAccountEquals(final PhoneAccount expected,
            final PhoneAccount actual) {
        assertEquals(expected.getAddress(), actual.getAddress());
        assertEquals(expected.getAccountHandle(), actual.getAccountHandle());
        assertEquals(expected.getCapabilities(), actual.getCapabilities());
        assertTrue(areBundlesEqual(expected.getExtras(), actual.getExtras()));
        assertEquals(expected.getHighlightColor(), actual.getHighlightColor());
        assertEquals(expected.getIcon(), actual.getIcon());
        assertEquals(expected.getLabel(), actual.getLabel());
        assertEquals(expected.getShortDescription(), actual.getShortDescription());
        assertEquals(expected.getSubscriptionAddress(), actual.getSubscriptionAddress());
        assertEquals(expected.getSupportedUriSchemes(), actual.getSupportedUriSchemes());
    }

    void assertPhoneAccountRegistered(final PhoneAccountHandle handle) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return mTelecomManager.getPhoneAccount(handle) != null;
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Phone account registration failed for " + handle
        );
    }

    void assertPhoneAccountEnabled(final PhoneAccountHandle handle) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        PhoneAccount phoneAccount = mTelecomManager.getPhoneAccount(handle);
                        return (phoneAccount != null && phoneAccount.isEnabled());
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Phone account enable failed for " + handle
        );
    }

    void assertPhoneAccountIsDefault(final PhoneAccountHandle handle) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        PhoneAccountHandle phoneAccountHandle =
                                mTelecomManager.getUserSelectedOutgoingPhoneAccount();
                        return (phoneAccountHandle != null && phoneAccountHandle.equals(handle));
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Failed to set default phone account to " + handle
        );
    }

    void assertCtsConnectionServiceUnbound() {
        if (CtsConnectionService.isBound()) {
            assertTrue("CtsConnectionService not yet unbound!",
                    CtsConnectionService.waitForUnBinding());
        }
    }

    void assertMockInCallServiceUnbound() {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return false;
                    }

                    @Override
                    public Object actual() {
                        return MockInCallService.isServiceBound();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "MockInCallService not yet unbound!"
        );
    }

    void assertIsOutgoingCallPermitted(boolean isPermitted, PhoneAccountHandle handle) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return isPermitted;
                    }

                    @Override
                    public Object actual() {
                        return mTelecomManager.isOutgoingCallPermitted(handle);
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Expected isOutgoingCallPermitted to be " + isPermitted
        );
    }

    void assertIsIncomingCallPermitted(boolean isPermitted, PhoneAccountHandle handle) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return isPermitted;
                    }

                    @Override
                    public Object actual() {
                        return mTelecomManager.isIncomingCallPermitted(handle);
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Expected isIncomingCallPermitted to be " + isPermitted
        );
    }

    void assertIsInCall(boolean isIncall) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return isIncall;
                    }

                    @Override
                    public Object actual() {
                        return mTelecomManager.isInCall();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Expected isInCall to be " + isIncall
        );
    }

    void assertIsInManagedCall(boolean isIncall) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return isIncall;
                    }

                    @Override
                    public Object actual() {
                        return mTelecomManager.isInManagedCall();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Expected isInManagedCall to be " + isIncall
        );
    }

    /**
     * Asserts that a call's properties are as expected.
     *
     * @param call The call.
     * @param properties The expected properties.
     */
    public void assertCallProperties(final Call call, final int properties) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return call.getDetails().hasProperty(properties);
                    }
                },
                TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Call should have properties " + properties
        );
    }

    /**
     * Asserts that a call does not have any of the specified call capability bits specified.
     *
     * @param call The call.
     * @param capabilities The capability or capabilities which are not expected.
     */
    public void assertDoesNotHaveCallCapabilities(final Call call, final int capabilities) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        int callCapabilities = call.getDetails().getCallCapabilities();
                        return !Call.Details.hasProperty(callCapabilities, capabilities);
                    }
                },
                TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Call should not have capabilities " + capabilities
        );
    }

    /**
     * Asserts that a call does not have any of the specified call property bits specified.
     *
     * @param call The call.
     * @param properties The property or properties which are not expected.
     */
    public void assertDoesNotHaveCallProperties(final Call call, final int properties) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return !call.getDetails().hasProperty(properties);
                    }
                },
                TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Call should not have properties " + properties
        );
    }

    /**
     * Asserts that the audio manager reports the specified audio mode.
     *
     * @param audioManager The audio manager to check.
     * @param expectedMode The expected audio mode.
     */
    public void assertAudioMode(final AudioManager audioManager, final int expectedMode) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return audioManager.getMode() == expectedMode;
                    }
                },
                TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Audio mode was expected to be " + expectedMode
        );
    }

    /**
     * Asserts that a call's capabilities are as expected.
     *
     * @param call The call.
     * @param capabilities The expected capabiltiies.
     */
    public void assertCallCapabilities(final Call call, final int capabilities) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return (call.getDetails().getCallCapabilities() & capabilities) ==
                                capabilities;
                    }
                },
                TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Call should have properties " + capabilities
        );
    }

    MockInCallService getInCallService() {
        return (mInCallCallbacks == null) ? null : mInCallCallbacks.getService();
    }

    /**
     * Asserts that the {@link UiModeManager} mode matches the specified mode.
     *
     * @param uiMode The expected ui mode.
     */
    public void assertUiMode(final int uiMode) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return uiMode;
                    }

                    @Override
                    public Object actual() {
                        return mUiModeManager.getCurrentModeType();
                    }
                },
                TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Expected ui mode " + uiMode
        );
    }

    void waitUntilConditionIsTrueOrTimeout(Condition condition, long timeout,
            String description) {
        final long start = System.currentTimeMillis();
        while (!Objects.equals(condition.expected(), condition.actual())
                && System.currentTimeMillis() - start < timeout) {
            sleep(50);
        }
        assertEquals(description, condition.expected(), condition.actual());
    }

    /**
     * Performs some work, and waits for the condition to be met.  If the condition is not met in
     * each step of the loop, the work is performed again.
     *
     * @param work The work to perform.
     * @param condition The condition.
     * @param timeout The timeout.
     * @param description Description of the work being performed.
     */
    void doWorkAndWaitUntilConditionIsTrueOrTimeout(Work work, Condition condition, long timeout,
            String description) {
        final long start = System.currentTimeMillis();
        work.doWork();
        while (!condition.expected().equals(condition.actual())
                && System.currentTimeMillis() - start < timeout) {
            sleep(50);
            work.doWork();
        }
        assertEquals(description, condition.expected(), condition.actual());
    }

    protected interface Condition {
        Object expected();
        Object actual();
    }

    protected interface Work {
        void doWork();
    }

    public static boolean areBundlesEqual(Bundle extras, Bundle newExtras) {
        if (extras == null || newExtras == null) {
            return extras == newExtras;
        }

        if (extras.size() != newExtras.size()) {
            return false;
        }

        for (String key : extras.keySet()) {
            if (key != null) {
                final Object value = extras.get(key);
                final Object newValue = newExtras.get(key);
                if (!Objects.equals(value, newValue)) {
                    return false;
                }
            }
        }
        return true;
    }
}
