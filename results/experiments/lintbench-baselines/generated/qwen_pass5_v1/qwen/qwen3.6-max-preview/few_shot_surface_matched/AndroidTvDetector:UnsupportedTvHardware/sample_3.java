package com.android.tools.lint.checks;

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
            "The `<uses-feature>` element should not require this unsupported TV hardware feature. "
                    + "Any uses-feature not explicitly marked with `required=\"false\"` is necessary on the device "
                    + "to be installed on. Ensure that any features that might prevent it from being installed on "
                    + "a TV device are reviewed and marked as not required in the manifest.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final Set<String> UNSUPPORTED_FEATURES = new HashSet<>(Arrays.asList(
            "android.hardware.touchscreen",
            "android.hardware.camera",
            "android.hardware.camera.autofocus",
            "android.hardware.camera.flash",
            "android.hardware.camera.front",
            "android.hardware.nfc",
            "android.hardware.telephony",
            "android.hardware.microphone"
    ));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses-feature");
    }

    @Override
    public void beforeCheckFile(Context context) {
        // State initialization before parsing each manifest file
    }

    @Override
    public void afterCheckFile(Context context) {
        // State cleanup after parsing each manifest file
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttributeNS(ANDROID_URI, "name");
        if (name == null || name.isEmpty()) {
            return;
        }

        if (UNSUPPORTED_FEATURES.contains(name)) {
            String required = element.getAttributeNS(ANDROID_URI, "required");
            if (!"false".equals(required)) {
                context.report(ISSUE, element, context.getLocation(element),
                        "This hardware feature is not supported on Android TV devices. "
                                + "Set android:required=\"false\" to allow installation on TV.");
            }
        }
    }
}