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

package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.TAG_USES_FEATURE;

/**
 * Checks for Android TV hardware feature requirements that are unsupported on TV devices.
 */
public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            AndroidTvDetector.class,
            Scope.MANIFEST_SCOPE);

    /** Using a hardware feature that is not supported on Android TV */
    public static final Issue UNSUPPORTED_TV_HARDWARE = Issue.create(
            "UnsupportedTvHardware",
            "Unsupported TV Hardware Feature",
            "The `<uses-feature>` element should not require this unsupported TV hardware " +
            "feature. Any uses-feature not explicitly marked with `required=\"false\"` is " +
            "necessary on the device to be installed on. Ensure that any features that might " +
            "prevent it from being installed on a TV device are reviewed and marked as not " +
            "required in the manifest.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            IMPLEMENTATION)
            .addMoreInfo(
                    "https://developer.android.com/training/tv/start/hardware.html#unsupported-features");

    /** Hardware features that are not supported on Android TV */
    private static final Set<String> UNSUPPORTED_TV_HARDWARE_FEATURES = new HashSet<>(
            Arrays.asList(
                    "android.hardware.bluetooth",
                    "android.hardware.camera",
                    "android.hardware.camera.autofocus",
                    "android.hardware.camera.capability.manual_post_processing",
                    "android.hardware.camera.capability.manual_sensor",
                    "android.hardware.camera.capability.raw",
                    "android.hardware.camera.flash",
                    "android.hardware.camera.front",
                    "android.hardware.camera.level.full",
                    "android.hardware.location.gps",
                    "android.hardware.nfc",
                    "android.hardware.nfc.hce",
                    "android.hardware.sensor.accelerometer",
                    "android.hardware.sensor.barometer",
                    "android.hardware.sensor.compass",
                    "android.hardware.sensor.gyroscope",
                    "android.hardware.sensor.light",
                    "android.hardware.sensor.proximity",
                    "android.hardware.sensor.stepcounter",
                    "android.hardware.sensor.stepdetector",
                    "android.hardware.telephony",
                    "android.hardware.telephony.cdma",
                    "android.hardware.telephony.gsm",
                    "android.hardware.touchscreen",
                    "android.hardware.touchscreen.multitouch",
                    "android.hardware.touchscreen.multitouch.distinct",
                    "android.hardware.touchscreen.multitouch.jazzhand",
                    "android.hardware.usb.accessory",
                    "android.hardware.usb.host"
            ));

    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";

    /** Constructs a new {@link AndroidTvDetector} */
    public AndroidTvDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES_FEATURE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Get the feature name
        Attr nameAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
        if (nameAttr == null) {
            return;
        }

        String featureName = nameAttr.getValue();
        if (featureName == null || featureName.isEmpty()) {
            return;
        }

        // Check if this is an unsupported TV hardware feature
        if (!UNSUPPORTED_TV_HARDWARE_FEATURES.contains(featureName)) {
            return;
        }

        // Check if it's marked as required="false"
        Attr requiredAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_REQUIRED);
        if (requiredAttr != null) {
            String requiredValue = requiredAttr.getValue();
            if ("false".equals(requiredValue)) {
                // Explicitly not required, so this is fine
                return;
            }
        }

        // Report the issue
        String message = String.format(
                "Unsupported TV hardware feature `%1$s`",
                featureName);

        context.report(
                UNSUPPORTED_TV_HARDWARE,
                element,
                context.getNameLocation(element),
                message);
    }
}