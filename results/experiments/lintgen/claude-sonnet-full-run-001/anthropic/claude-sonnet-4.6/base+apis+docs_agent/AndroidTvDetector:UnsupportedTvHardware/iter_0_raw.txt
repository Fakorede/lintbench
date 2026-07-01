package com.android.tools.lint.checks;

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
import java.util.Set;

/**
 * Detector for unsupported TV hardware features declared in the Android manifest.
 */
public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue UNSUPPORTED_TV_HARDWARE = Issue.create(
            "UnsupportedTvHardware",
            "Unsupported TV Hardware Feature",
            "The `<uses-feature>` element should not require this unsupported TV hardware feature. "
                    + "Any uses-feature not explicitly marked with `required=\"false\"` is necessary "
                    + "on the device to be installed on. Ensure that any features that might prevent "
                    + "it from being installed on a TV device are reviewed and marked as not required "
                    + "in the manifest.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE))
            .addMoreInfo("https://developer.android.com/training/tv/start/hardware.html#unsupported-features");

    private static final String ANDROID_MANIFEST_TAG_USES_FEATURE = "uses-feature";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";
    private static final String VALUE_FALSE = "false";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    /**
     * Hardware features that are not supported on Android TV devices.
     * Reference: https://developer.android.com/training/tv/start/hardware.html#unsupported-features
     */
    private static final Set<String> UNSUPPORTED_TV_HARDWARE_FEATURES = new HashSet<>(Arrays.asList(
            "android.hardware.bluetooth",
            "android.hardware.camera",
            "android.hardware.camera.autofocus",
            "android.hardware.camera.capability.manual_post_processing",
            "android.hardware.camera.capability.manual_sensor",
            "android.hardware.camera.capability.raw",
            "android.hardware.camera.flash",
            "android.hardware.camera.front",
            "android.hardware.camera.level.full",
            "android.hardware.consumerir",
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
            "android.hardware.wifi"
    ));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(ANDROID_MANIFEST_TAG_USES_FEATURE);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Get the feature name
        Attr nameAttr = element.getAttributeNodeNS(ANDROID_NS, ATTR_NAME);
        if (nameAttr == null) {
            // Try without namespace
            nameAttr = element.getAttributeNode(ATTR_NAME);
        }

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

        // Check if required="false" is set
        Attr requiredAttr = element.getAttributeNodeNS(ANDROID_NS, ATTR_REQUIRED);
        if (requiredAttr == null) {
            requiredAttr = element.getAttributeNode(ATTR_REQUIRED);
        }

        if (requiredAttr != null && VALUE_FALSE.equals(requiredAttr.getValue())) {
            // Feature is explicitly marked as not required, so it's fine
            return;
        }

        // Report the issue
        String message = String.format(
                "Unsupported TV hardware feature `%s`", featureName);

        context.report(
                UNSUPPORTED_TV_HARDWARE,
                element,
                context.getLocation(nameAttr != null ? nameAttr : element),
                message);
    }
}