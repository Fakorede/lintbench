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
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "UnsupportedTvHardware",
                    "Unsupported TV Hardware Feature",
                    "The `<uses-feature>` element should not require this unsupported TV "
                            + "hardware feature. Any uses-feature not explicitly marked with "
                            + "`required=\"false\"` is necessary on the device to be installed "
                            + "on. Ensure that any features that might prevent it from being "
                            + "installed on a TV device are reviewed and marked as not "
                            + "required in the manifest.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    // Hardware features unsupported on Android TV
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
                            "android.hardware.wifi",
                            "android.hardware.wifi.direct"));

    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String ATTR_NAME = "android:name";
    private static final String ATTR_REQUIRED = "android:required";
    private static final String VALUE_FALSE = "false";

    // Whether this is a TV manifest (has the TV launcher category intent filter)
    private boolean mIsTvManifest;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIsTvManifest = false;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        // Nothing to do after file check
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if (!TAG_USES_FEATURE.equals(tagName)) {
            return;
        }

        // Get the feature name
        Attr nameAttr = element.getAttributeNode("android:name");
        if (nameAttr == null) {
            // Try without namespace prefix (some manifests may omit it)
            nameAttr = element.getAttributeNode("name");
        }
        if (nameAttr == null) {
            return;
        }

        String featureName = nameAttr.getValue();
        if (featureName == null || featureName.isEmpty()) {
            return;
        }

        // Check if this feature is unsupported on TV
        if (!UNSUPPORTED_TV_HARDWARE_FEATURES.contains(featureName)) {
            return;
        }

        // Check if required is explicitly set to false
        Attr requiredAttr = element.getAttributeNode("android:required");
        if (requiredAttr == null) {
            requiredAttr = element.getAttributeNode("required");
        }

        if (requiredAttr != null && VALUE_FALSE.equals(requiredAttr.getValue())) {
            // Feature is explicitly marked as not required, no issue
            return;
        }

        // Report the issue
        context.report(
                ISSUE,
                element,
                context.getLocation(nameAttr),
                String.format(
                        "Expecting `android:required=\"false\"` for this hardware "
                                + "feature that may not be supported by all Android TV devices: `%1$s`",
                        featureName));
    }
}