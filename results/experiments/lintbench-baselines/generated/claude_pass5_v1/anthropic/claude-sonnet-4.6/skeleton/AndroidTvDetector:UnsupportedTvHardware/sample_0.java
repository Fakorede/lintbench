package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String ATTR_NAME = "android:name";
    private static final String ATTR_REQUIRED = "android:required";
    private static final String TAG_USES_PERMISSION = "uses-permission";
    private static final String MANIFEST_TAG = "manifest";

    /**
     * Hardware features that are not supported on Android TV devices.
     * See https://developer.android.com/training/tv/start/hardware.html#unsupported-features
     */
    private static final Set<String> UNSUPPORTED_TV_HARDWARE_FEATURES =
            Collections.unmodifiableSet(
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
                                    "android.hardware.location.network",
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
                                    "android.hardware.wifi")));

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue UNSUPPORTED_TV_HARDWARE =
            Issue.create(
                    "UnsupportedTvHardware",
                    "Unsupported TV Hardware Feature",
                    "The `<uses-feature>` element should not require this unsupported TV "
                            + " hardware feature. Any uses-feature not explicitly marked with "
                            + " `required=\"false\"` is necessary on the device to be installed "
                            + " on. Ensure that any features that might prevent it from being "
                            + " installed on a TV device are reviewed and marked as not "
                            + " required in the manifest.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    /** Whether the current manifest targets TV (has the TV leanback feature declared). */
    private boolean mIsTvApp;

    public AndroidTvDetector() {}

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_USES_FEATURE, TAG_USES_PERMISSION);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIsTvApp = false;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        // Reset state after processing each file
        mIsTvApp = false;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        if (TAG_USES_FEATURE.equals(tagName)) {
            // Check if this is a leanback feature declaration (marks app as TV app)
            String featureName = element.getAttribute(ATTR_NAME);
            if (featureName == null || featureName.isEmpty()) {
                featureName = element.getAttributeNS(
                        "http://schemas.android.com/apk/res/android", "name");
            }

            if ("android.software.leanback".equals(featureName)) {
                mIsTvApp = true;
            }

            // Check if this feature is unsupported on TV and required
            if (UNSUPPORTED_TV_HARDWARE_FEATURES.contains(featureName)) {
                // Check if required attribute is explicitly set to false
                String required = element.getAttribute(ATTR_REQUIRED);
                if (required == null || required.isEmpty()) {
                    required = element.getAttributeNS(
                            "http://schemas.android.com/apk/res/android", "required");
                }

                // If required is not explicitly "false", report the issue
                if (!"false".equals(required)) {
                    context.report(
                            UNSUPPORTED_TV_HARDWARE,
                            element,
                            context.getLocation(element),
                            String.format(
                                    "Expecting `android:required=\"false\"` for this hardware "
                                            + "feature that may not be supported by all Android TV "
                                            + "devices: `%1$s`",
                                    featureName));
                }
            }
        }
    }
}