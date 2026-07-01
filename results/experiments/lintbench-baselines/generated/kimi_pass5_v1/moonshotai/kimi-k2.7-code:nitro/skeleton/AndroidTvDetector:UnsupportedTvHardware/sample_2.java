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
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final String[] UNSUPPORTED_TV_FEATURES = {
            "android.hardware.camera",
            "android.hardware.location.gps",
            "android.hardware.microphone",
            "android.hardware.nfc",
            "android.hardware.sensor.accelerometer",
            "android.hardware.sensor.barometer",
            "android.hardware.sensor.compass",
            "android.hardware.sensor.gyroscope",
            "android.hardware.sensor.light",
            "android.hardware.sensor.proximity",
            "android.hardware.sensor.stepcounter",
            "android.hardware.sensor.stepdetector",
            "android.hardware.telephony",
            "android.hardware.touchscreen"
    };

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "UnsupportedTvHardware",
                    "Unsupported TV Hardware Feature",
                    "The <uses-feature> element should not require unsupported TV hardware features. "
                            + "Features such as camera, GPS, NFC, telephony, microphone, sensors, "
                            + "and touchscreen are not available on TV devices. Any <uses-feature> "
                            + "not explicitly marked with required=\"false\" is considered required, "
                            + "which prevents the app from being installed on TV devices. Mark "
                            + "unsupported features as not required.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mCheckFile;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses-feature");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mCheckFile = "AndroidManifest.xml".equals(context.getFile().getName());
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        mCheckFile = false;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!mCheckFile) {
            return;
        }

        String name = element.getAttributeNS(ANDROID_URI, "name");
        if (name.isEmpty()) {
            name = element.getAttribute("name");
        }
        if (name.isEmpty()) {
            return;
        }

        String required = element.getAttributeNS(ANDROID_URI, "required");
        if (required.isEmpty()) {
            required = element.getAttribute("required");
        }
        if ("false".equalsIgnoreCase(required)) {
            return;
        }

        if (isUnsupportedTvFeature(name)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "The feature \""
                            + name
                            + "\" is not supported on TV devices and should not be required. "
                            + "Add android:required=\"false\" if this feature is optional.");
        }
    }

    private static boolean isUnsupportedTvFeature(@NonNull String name) {
        for (String feature : UNSUPPORTED_TV_FEATURES) {
            if (feature.equals(name)) {
                return true;
            }
            if (name.startsWith(feature + ".")) {
                return true;
            }
        }
        return false;
    }
}