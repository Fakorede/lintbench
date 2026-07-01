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
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "UnsupportedTvHardware",
            "Unsupported TV Hardware Feature",
            "The <uses-feature> element should not require this unsupported TV hardware feature. " +
            "Any uses-feature not explicitly marked with required=\"false\" is necessary on the device " +
            "to be installed on. Ensure that any features that might prevent it from being installed on " +
            "a TV device are reviewed and marked as not required in the manifest.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final Set<String> UNSUPPORTED_TV_FEATURES = new HashSet<>(Arrays.asList(
            "android.hardware.touchscreen",
            "android.hardware.faketouch",
            "android.hardware.telephony",
            "android.hardware.camera",
            "android.hardware.nfc",
            "android.hardware.sensor.accelerometer",
            "android.hardware.sensor.barometer",
            "android.hardware.sensor.compass",
            "android.hardware.sensor.gyroscope",
            "android.hardware.sensor.light",
            "android.hardware.sensor.proximity",
            "android.hardware.sensor.stepcounter",
            "android.hardware.sensor.stepdetector"
    ));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses-feature");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        // Per-file initialization placeholder
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        // Per-file cleanup placeholder
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String featureName = element.getAttribute("android:name");
        if (featureName.isEmpty() || !UNSUPPORTED_TV_FEATURES.contains(featureName)) {
            return;
        }

        String requiredAttr = element.getAttribute("android:required");
        boolean isRequired = !"false".equals(requiredAttr);

        if (isRequired) {
            context.report(ISSUE, element, context.getLocation(element),
                    "This hardware feature is not supported on Android TV. " +
                    "Add android:required=\"false\" to allow installation on TV devices.");
        }
    }
}