package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
 * Detector for unsupported TV hardware features declared in the manifest.
 */
public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            AndroidTvDetector.class,
            Scope.MANIFEST_SCOPE);

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

    /**
     * Hardware features that are not supported on TV devices.
     * See https://developer.android.com/training/tv/start/hardware.html#unsupported-features
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

    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String ATTR_NAME = "android:name";
    private static final String ATTR_REQUIRED = "android:required";
    private static final String VALUE_FALSE = "false";

    /** Constructs a new {@link AndroidTvDetector} */
    public AndroidTvDetector() {
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES_FEATURE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Get the feature name
        String featureName = element.getAttribute(ATTR_NAME);
        if (featureName == null || featureName.isEmpty()) {
            // Try without namespace prefix
            featureName = element.getAttribute("name");
            if (featureName == null || featureName.isEmpty()) {
                return;
            }
        }

        // Check if this is an unsupported TV hardware feature
        if (!UNSUPPORTED_TV_HARDWARE_FEATURES.contains(featureName)) {
            return;
        }

        // Check if it's explicitly marked as not required
        String requiredValue = element.getAttribute(ATTR_REQUIRED);
        if (requiredValue == null || requiredValue.isEmpty()) {
            // Try without namespace prefix
            requiredValue = element.getAttribute("required");
        }

        // If required is explicitly set to false, this is fine
        if (VALUE_FALSE.equals(requiredValue)) {
            return;
        }

        // Report the issue - the feature is required (either explicitly or by default)
        Attr nameAttr = element.getAttributeNode(ATTR_NAME);
        if (nameAttr == null) {
            nameAttr = element.getAttributeNode("name");
        }

        String message = String.format(
                "Unsupported TV hardware feature `%1$s`",
                featureName);

        if (nameAttr != null) {
            context.report(UNSUPPORTED_TV_HARDWARE, element, context.getLocation(nameAttr),
                    message);
        } else {
            context.report(UNSUPPORTED_TV_HARDWARE, element, context.getLocation(element),
                    message);
        }
    }
}