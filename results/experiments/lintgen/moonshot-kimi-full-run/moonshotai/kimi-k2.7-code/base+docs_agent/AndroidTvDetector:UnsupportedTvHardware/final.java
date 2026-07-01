package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String USES_FEATURE = "uses-feature";

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";

    public static final Issue ISSUE = Issue.create(
            "UnsupportedTvHardware",
            "Unsupported TV hardware feature required",
            "The `<uses-feature>` element should not require an unsupported TV hardware feature. "
                    + "Any `<uses-feature>` not explicitly marked with `android:required=\"false\"` "
                    + "is treated as required and can prevent the app from being installed on TV devices. "
                    + "Review features that might prevent installation on a TV and mark them as not required "
                    + "in the manifest.\n"
                    + "Reference: https://developer.android.com/training/tv/start/hardware.html#unsupported-features",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    @Override
    @NonNull
    public Collection<String> getApplicableElements() {
        return Arrays.asList(USES_FEATURE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        if (!isUnsupportedTvFeature(name)) {
            return;
        }

        String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
        if ("false".equals(required)) {
            return;
        }

        String message = "The `<uses-feature>` element should not require unsupported TV hardware feature `"
                + name + "`. Mark it with `android:required=\"false\"` or remove it.";
        context.report(ISSUE, context.getElementLocation(element), message);
    }

    private static boolean isUnsupportedTvFeature(@NonNull String name) {
        return "android.hardware.touchscreen".equals(name)
                || name.startsWith("android.hardware.touchscreen.")
                || "android.hardware.faketouch".equals(name)
                || "android.hardware.telephony".equals(name)
                || name.startsWith("android.hardware.telephony.")
                || "android.hardware.camera".equals(name)
                || name.startsWith("android.hardware.camera.")
                || "android.hardware.nfc".equals(name)
                || "android.hardware.location.gps".equals(name)
                || "android.hardware.sensor".equals(name)
                || name.startsWith("android.hardware.sensor.");
    }
}