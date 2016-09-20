/**
 * Copyright (C) 2016 The CyanogenMod Project
 * Copyright (C) 2016 The MoKee Open Source Project
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

package org.mokee.platform.internal;

import android.content.Context;
import android.os.SystemProperties;
import android.util.Slog;
import com.android.server.LocalServices;
import com.android.server.SystemServiceManager;

import org.mokee.platform.internal.common.MKSystemServiceHelper;

/**
 * Base MK System Server which handles the starting and states of various MK
 * specific system services. Since its part of the main looper provided by the system
 * server, it will be available indefinitely (until all the things die).
 */
public class MKSystemServer {
    private static final String TAG = "MKSystemServer";
    private Context mSystemContext;
    private MKSystemServiceHelper mSystemServiceHelper;

    private static final String ENCRYPTING_STATE = "trigger_restart_min_framework";
    private static final String ENCRYPTED_STATE = "1";

    public MKSystemServer(Context systemContext) {
        mSystemContext = systemContext;
        mSystemServiceHelper = new MKSystemServiceHelper(mSystemContext);
    }

    public static boolean coreAppsOnly() {
        // Only run "core" apps+services if we're encrypting the device.
        final String cryptState = SystemProperties.get("vold.decrypt");
        return ENCRYPTING_STATE.equals(cryptState) ||
               ENCRYPTED_STATE.equals(cryptState);
    }

    /**
     * Invoked via reflection by the SystemServer
     */
    private void run() {
        // Start services.
        try {
            startServices();
        } catch (Throwable ex) {
            Slog.e("System", "******************************************");
            Slog.e("System", "************ Failure starting mk system services", ex);
            throw ex;
        }
    }

    private void startServices() {
        final Context context = mSystemContext;
        final SystemServiceManager ssm = LocalServices.getService(SystemServiceManager.class);
        String[] externalServices = context.getResources().getStringArray(
                org.mokee.platform.internal.R.array.config_externalMKServices);

        for (String service : externalServices) {
            try {
                Slog.i(TAG, "Attempting to start service " + service);
                MKSystemService mkSystemService =  mSystemServiceHelper.getServiceFor(service);
                if (context.getPackageManager().hasSystemFeature(
                        mkSystemService.getFeatureDeclaration())) {
                    if (coreAppsOnly() && !mkSystemService.isCoreService()) {
                        Slog.d(TAG, "Not starting " + service +
                                " - only parsing core apps");
                    } else {
                        Slog.i(TAG, "Starting service " + service);
                        ssm.startService(mkSystemService.getClass());
                    }
                } else {
                    Slog.i(TAG, "Not starting service " + service +
                            " due to feature not declared on device");
                }
            } catch (Throwable e) {
                reportWtf("starting " + service , e);
            }
        }
    }

    private void reportWtf(String msg, Throwable e) {
        Slog.w(TAG, "***********************************************");
        Slog.wtf(TAG, "BOOT FAILURE " + msg, e);
    }
}
