package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final Set<String> UNSUPPORTED_FEATURES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "android.hardware.touchscreen",
            "android.hardware.touchscreen.multitouch",
            "android.hardware.touchscreen.multitouch.distinct",
            "android.hardware.faketouch",
            "android.hardware.telephony",
            "android.hardware.camera",
            "android.hardware.camera.autofocus",
            "android.hardware.camera.flash",
            "android.hardware.camera.any",
            "android.hardware.nfc",
            "android.hardware.location",
            "android.hardware.location.gps",
            "android.hardware.location.network",
            "android.hardware.microphone",
            "android.hardware.sensor.accelerometer",
            "android.hardware.sensor.barometer",
            "android.hardware.sensor.compass",
            "android.hardware.sensor.gyroscope",
            "android.hardware.sensor.light",
            "android.hardware.sensor.proximity",
            "android.hardware.sensor.stepcounter",
            "android.hardware.sensor.stepdetector"
    )));

    public static final Issue ISSUE = Issue.create(
            "UnsupportedTvHardware",
            "Unsupported TV hardware feature",
            "The `<uses-feature>` element should not require unsupported TV hardware features. "
                    + "Any `<uses-feature>` not explicitly marked with `required=\"false\"` is "
                    + "treated as required for installation. Features that are not supported on "
                    + "TV devices should be marked as not required in the manifest so the app can "
                    + "still be installed on TVs.\n"
                    + "Refer to the Android TV hardware documentation for more details.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    @Override
    @NotNull
    public List<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_USES_FEATURE);
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
        if (name.isEmpty() || !UNSUPPORTED_FEATURES.contains(name)) {
            return;
        }

        String required = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_REQUIRED);
        if ("false".equals(required)) {
            return;
        }

        String message = String.format(
                "The feature \"%1$s\" is not supported on TV devices and should be marked with "
                        + "`required=\"false\"` to allow installation on TVs.",
                name);
        context.report(ISSUE, context.getLocation(element), message);
    }
}