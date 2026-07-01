package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
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

/**
 * Checks for unsupported TV hardware features declared in the manifest.
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
            .addMoreInfo("https://developer.android.com/training/tv/start/hardware.html#unsupported-features");

    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String ATTR_NAME = "android:name";
    private static final String ATTR_REQUIRED = "android:required";
    private static final String ATTR_ANDROID_NAME = "name";
    private static final String ATTR_ANDROID_REQUIRED = "required";

    /**
     * Hardware features that are not supported on Android TV devices.
     * Source: https://developer.android.com/training/tv/start/hardware.html#unsupported-features
     */
    private static final Set<String> UNSUPPORTED_TV_HARDWARE_FEATURES;

    static {
        Set<String> features = new HashSet<>();
        // Touchscreen features
        features.add("android.hardware.touchscreen");
        features.add("android.hardware.faketouch");
        features.add("android.hardware.touchscreen.multitouch");
        features.add("android.hardware.touchscreen.multitouch.distinct");
        features.add("android.hardware.touchscreen.multitouch.jazzhand");
        features.add("android.hardware.faketouch.multitouch.distinct");
        features.add("android.hardware.faketouch.multitouch.jazzhand");

        // Telephony features
        features.add("android.hardware.telephony");
        features.add("android.hardware.telephony.cdma");
        features.add("android.hardware.telephony.gsm");

        // Camera features
        features.add("android.hardware.camera");
        features.add("android.hardware.camera.autofocus");
        features.add("android.hardware.camera.flash");
        features.add("android.hardware.camera.front");
        features.add("android.hardware.camera.any");
        features.add("android.hardware.camera.capability.manual_post_processing");
        features.add("android.hardware.camera.capability.manual_sensor");
        features.add("android.hardware.camera.capability.raw");
        features.add("android.hardware.camera.level.full");

        // Location features
        features.add("android.hardware.location.gps");
        features.add("android.hardware.location.network");

        // Microphone
        features.add("android.hardware.microphone");

        // NFC
        features.add("android.hardware.nfc");
        features.add("android.hardware.nfc.hce");

        // Sensor features
        features.add("android.hardware.sensor.accelerometer");
        features.add("android.hardware.sensor.barometer");
        features.add("android.hardware.sensor.compass");
        features.add("android.hardware.sensor.gyroscope");
        features.add("android.hardware.sensor.light");
        features.add("android.hardware.sensor.proximity");
        features.add("android.hardware.sensor.stepcounter");
        features.add("android.hardware.sensor.stepdetector");

        // Screen features
        features.add("android.hardware.screen.landscape");
        features.add("android.hardware.screen.portrait");

        // WiFi features
        features.add("android.hardware.wifi");
        features.add("android.hardware.wifi.direct");

        // Bluetooth
        features.add("android.hardware.bluetooth");
        features.add("android.hardware.bluetooth_le");

        // USB
        features.add("android.hardware.usb.accessory");
        features.add("android.hardware.usb.host");

        // Audio
        features.add("android.hardware.audio.low_latency");

        // Consumer IR
        features.add("android.hardware.consumerir");

        UNSUPPORTED_TV_HARDWARE_FEATURES = Collections.unmodifiableSet(features);
    }

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
        String featureName = element.getAttributeNS(
                "http://schemas.android.com/apk/res/android", ATTR_ANDROID_NAME);

        if (featureName == null || featureName.isEmpty()) {
            // Try without namespace
            featureName = element.getAttribute(ATTR_ANDROID_NAME);
        }

        if (featureName == null || featureName.isEmpty()) {
            return;
        }

        if (!UNSUPPORTED_TV_HARDWARE_FEATURES.contains(featureName)) {
            return;
        }

        // Check if required="false" is set
        String requiredValue = element.getAttributeNS(
                "http://schemas.android.com/apk/res/android", ATTR_ANDROID_REQUIRED);

        if (requiredValue == null || requiredValue.isEmpty()) {
            // Try without namespace
            requiredValue = element.getAttribute(ATTR_ANDROID_REQUIRED);
        }

        // If required is explicitly set to false, this is fine
        if ("false".equals(requiredValue)) {
            return;
        }

        // Report the issue - the feature is required (either explicitly or by default)
        Attr nameAttr = element.getAttributeNodeNS(
                "http://schemas.android.com/apk/res/android", ATTR_ANDROID_NAME);
        if (nameAttr == null) {
            nameAttr = element.getAttributeNode(ATTR_ANDROID_NAME);
        }

        String message = String.format(
                "Unsupported TV hardware feature `%1$s`", featureName);

        if (nameAttr != null) {
            context.report(UNSUPPORTED_TV_HARDWARE, element, context.getLocation(nameAttr),
                    message);
        } else {
            context.report(UNSUPPORTED_TV_HARDWARE, element, context.getLocation(element),
                    message);
        }
    }
}