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
import java.util.HashSet;
import java.util.Set;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "UnsupportedTvHardware",
            "Unsupported TV Hardware Feature",
            "The `<uses-feature>` element should not require this unsupported TV hardware feature. "
                    + "Any uses-feature not explicitly marked with `required=\"false\"` is necessary on the device "
                    + "to be installed on. Ensure that any features that might prevent it from being installed on a "
                    + "TV device are reviewed and marked as not required in the manifest.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";

    private static final Set<String> UNSUPPORTED_TV_FEATURES = new HashSet<>(Arrays.asList(
            "android.hardware.touchscreen",
            "android.hardware.camera",
            "android.hardware.camera.autofocus",
            "android.hardware.location",
            "android.hardware.location.network",
            "android.hardware.location.gps",
            "android.hardware.microphone",
            "android.hardware.sensor",
            "android.hardware.telephony",
            "android.hardware.nfc",
            "android.hardware.screen.portrait",
            "android.hardware.screen.landscape"
    ));

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        // Lifecycle hook for file scanning initialization
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        // Lifecycle hook for file scanning cleanup
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String featureName = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (featureName.isEmpty() || !UNSUPPORTED_TV_FEATURES.contains(featureName)) {
            return;
        }

        String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
        // The default value for android:required is "true". Only safe if explicitly "false".
        if ("false".equals(required)) {
            return;
        }

        context.report(ISSUE, element, context.getLocation(element),
                "Unsupported TV hardware feature: " + featureName + ". "
                        + "Mark as android:required=\"false\" to support Android TV devices.");
    }
}