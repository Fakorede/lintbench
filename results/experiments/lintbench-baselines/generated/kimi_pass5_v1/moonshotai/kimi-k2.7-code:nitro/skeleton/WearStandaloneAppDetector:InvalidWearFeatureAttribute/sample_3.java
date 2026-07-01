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
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;

public class WearStandaloneAppDetector extends Detector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String WEARABLE_HARDWARE_FEATURE = "android.hardware.type.watch";

    private static final Implementation IMPLEMENTATION =
            new Implementation(WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidWearFeatureAttribute",
                    "Invalid attribute for Wear uses-feature",
                    "For the `android.hardware.type.watch` uses-feature, `android:required=\"false\"` is disallowed. "
                            + "A single APK for Wear and non-Wear devices is not supported.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public void beforeCheckFile(Context context) {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses-feature");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttributeNS(ANDROID_URI, "name");
        if (name.isEmpty()) {
            name = element.getAttribute("android:name");
        }
        if (!WEARABLE_HARDWARE_FEATURE.equals(name)) {
            return;
        }

        String required = element.getAttributeNS(ANDROID_URI, "required");
        if (required.isEmpty()) {
            required = element.getAttribute("android:required");
        }
        if ("false".equals(required)) {
            context.report(
                    ISSUE,
                    element,
                    "A single APK for Wear and non-Wear devices is not supported; "
                            + "do not set `android:required=\"false\"` for the `android.hardware.type.watch` uses-feature.");
        }
    }

    @Override
    public void afterCheckFile(Context context) {
    }
}