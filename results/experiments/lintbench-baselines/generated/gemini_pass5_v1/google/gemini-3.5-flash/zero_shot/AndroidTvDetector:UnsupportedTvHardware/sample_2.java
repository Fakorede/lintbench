package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    public static final Issue UNSUPPORTED_TV_HARDWARE = Issue.create(
        "UnsupportedTvHardware",
        "Unsupported TV Hardware Feature",
        "The `<uses-feature>` element should not require this unsupported TV hardware feature. " +
        "Any uses-feature not explicitly marked with `required=\"false\"` is necessary on the " +
        "device to be installed on. Ensure that any features that might prevent it from being " +
        "installed on a TV device are reviewed and marked as not required in the manifest.",
        Category.CORRECTNESS,
        6,
        Severity.ERROR,
        new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    private static final Set<String> UNSUPPORTED_FEATURES = new HashSet<>();

    static {
        // Touchscreen
        UNSUPPORTED_FEATURES.add("android.hardware.touchscreen");
        UNSUPPORTED_FEATURES.add("android.hardware.touchscreen.multitouch");
        UNSUPPORTED_FEATURES.add("android.hardware.touchscreen.multitouch.distinct");
        UNSUPPORTED_FEATURES.add("android.hardware.touchscreen.multitouch.jazzhand");

        // Telephony
        UNSUPPORTED_FEATURES.add("android.hardware.telephony");
        UNSUPPORTED_FEATURES.add("android.hardware.telephony.cdma");
        UNSUPPORTED_FEATURES.add("android.hardware.telephony.gsm");

        // Camera
        UNSUPPORTED_FEATURES.add("android.hardware.camera");
        UNSUPPORTED_FEATURES.add("android.hardware.camera.autofocus");
        UNSUPPORTED_FEATURES.add("android.hardware.camera.flash");
        UNSUPPORTED_FEATURES.add("android.hardware.camera.front");
        UNSUPPORTED_FEATURES.add("android.hardware.camera.any");

        // NFC
        UNSUPPORTED_FEATURES.add("android.hardware.nfc");
        UNSUPPORTED_FEATURES.add("android.hardware.nfc.hce");

        // GPS / Location
        UNSUPPORTED_FEATURES.add("android.hardware.location.gps");

        // Sensors
        UNSUPPORTED_FEATURES.add("android.hardware.sensor.accelerometer");
        UNSUPPORTED_FEATURES.add("android.hardware.sensor.barometer");
        UNSUPPORTED_FEATURES.add("android.hardware.sensor.compass");
        UNSUPPORTED_FEATURES.add("android.hardware.sensor.gyroscope");
        UNSUPPORTED_FEATURES.add("android.hardware.sensor.light");
        UNSUPPORTED_FEATURES.add("android.hardware.sensor.proximity");
        UNSUPPORTED_FEATURES.add("android.hardware.sensor.stepcounter");
        UNSUPPORTED_FEATURES.add("android.hardware.sensor.stepdetector");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_USES_FEATURE);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
        if (UNSUPPORTED_FEATURES.contains(name)) {
            String required = element.getAttributeNS(SdkConstants.ANDROID_URI, "required");
            if (!"false".equals(required)) {
                context.report(
                    UNSUPPORTED_TV_HARDWARE,
                    element,
                    context.getLocation(element),
                    "Expect `android:required=\"false\"` for TV-unsupported feature `" + name + "`"
                );
            }
        }
    }
}