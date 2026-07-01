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

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "UnsupportedTvHardware",
                    "Unsupported TV Hardware Feature",
                    "The `<uses-feature>` element should not require this unsupported TV hardware feature. " +
                    "Any uses-feature not explicitly marked with `required=\"false\"` is necessary on the device " +
                    "to be installed on. Ensure that any features that might prevent it from being installed on a " +
                    "TV device are reviewed and marked as not required in the manifest.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private static final Set<String> UNSUPPORTED_TV_FEATURES = new HashSet<>(Arrays.asList(
            "android.hardware.touchscreen",
            "android.hardware.camera",
            "android.hardware.camera.autofocus",
            "android.hardware.location.gps",
            "android.hardware.microphone",
            "android.hardware.sensor",
            "android.hardware.telephony"
    ));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses-feature");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        // No initialization required
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        // No cleanup required
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!context.isManifestFile()) {
            return;
        }

        String featureName = element.getAttributeNS(ANDROID_URI, "name");
        if (featureName.isEmpty() || !UNSUPPORTED_TV_FEATURES.contains(featureName)) {
            return;
        }

        String required = element.getAttributeNS(ANDROID_URI, "required");
        if ("false".equals(required)) {
            return;
        }

        context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "This hardware feature is not supported on Android TV devices. " +
                "Add `android:required=\"false\"` to allow installation on TV devices."
        );
    }
}