package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_REQUIRED;
import static com.android.SdkConstants.VALUE_TRUE;
import static com.android.xml.AndroidManifest.NODE_USES_FEATURE;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue UNSUPPORTED_TV_HARDWARE =
            Issue.create(
                    "UnsupportedTvHardware",
                    "Unsupported TV Hardware Feature",
                    "The `<uses-feature>` element should not require this unsupported TV hardware "
                            + "feature. Any uses-feature not explicitly marked with "
                            + "`required=\"false\"` is necessary on the device to be installed "
                            + "on. Ensure that any features that might prevent it from being "
                            + "installed on a TV device are reviewed and marked as not "
                            + "required in the manifest.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final Set<String> UNSUPPORTED_TV_HARDWARE_FEATURES =
            new HashSet<>(
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
                            "android.hardware.microphone",
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
                            "android.hardware.usb.host",
                            "android.hardware.wifi"));

    private boolean mIsAndroidTvApp;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIsAndroidTvApp = false;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        // Reset state after checking file
        mIsAndroidTvApp = false;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                NODE_USES_FEATURE,
                "intent-filter",
                "category",
                "uses-feature");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        // Check if this is a TV app by looking for the LEANBACK_LAUNCHER category
        if ("category".equals(tagName)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                mIsAndroidTvApp = true;
            }
            return;
        }

        if (!NODE_USES_FEATURE.equals(tagName) && !"uses-feature".equals(tagName)) {
            return;
        }

        String featureName = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (featureName == null || featureName.isEmpty()) {
            return;
        }

        if (!UNSUPPORTED_TV_HARDWARE_FEATURES.contains(featureName)) {
            return;
        }

        // Check if required is explicitly set to false
        String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
        if (required != null && !required.isEmpty() && !VALUE_TRUE.equals(required)) {
            // required="false" — this is fine
            return;
        }

        // Report the issue
        context.report(
                UNSUPPORTED_TV_HARDWARE,
                element,
                context.getLocation(element),
                String.format(
                        "Requiring feature `%s` is unsupported on TV and may prevent it "
                                + "from being installed on a TV device. Consider adding "
                                + "`required=\"false\"` to this `<uses-feature>` element.",
                        featureName));
    }
}