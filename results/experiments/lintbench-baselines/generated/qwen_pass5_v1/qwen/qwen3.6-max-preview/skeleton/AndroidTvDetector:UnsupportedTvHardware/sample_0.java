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
    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";
    private static final String TAG_USES_FEATURE = "uses-feature";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "UnsupportedTvHardware",
                    "Unsupported TV Hardware Feature",
                    "The `<uses-feature>` element should not require this unsupported TV hardware feature. " +
                    "Any uses-feature not explicitly marked with `required=\"false\"` is necessary on the device to be installed on. " +
                    "Ensure that any features that might prevent it from being installed on a TV device are reviewed and marked as not required in the manifest.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        // No state initialization required
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        // No state cleanup required
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
        if ("false".equals(required)) {
            return;
        }

        if (isUnsupportedTvFeature(name)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "This hardware feature is not supported on Android TV devices. " +
                    "Set android:required=\"false\" to allow installation on TV devices.");
        }
    }

    private static boolean isUnsupportedTvFeature(String name) {
        switch (name) {
            case "android.hardware.touchscreen":
            case "android.hardware.camera":
            case "android.hardware.nfc":
            case "android.hardware.telephony":
            case "android.hardware.microphone":
            case "android.hardware.location.gps":
                return true;
            default:
                return name.startsWith("android.hardware.sensor.");
        }
    }
}