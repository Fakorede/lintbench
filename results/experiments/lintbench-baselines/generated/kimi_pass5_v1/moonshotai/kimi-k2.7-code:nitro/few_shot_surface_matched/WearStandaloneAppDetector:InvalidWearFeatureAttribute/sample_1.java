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
    private static final String NODE_USES_FEATURE = "uses-feature";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";
    private static final String HARDWARE_TYPE_WATCH = "android.hardware.type.watch";

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidWearFeatureAttribute",
                    "Invalid Wear Feature Attribute",
                    "For the `android.hardware.type.watch` uses-feature, "
                            + "`android:required=\"false\"` is disallowed. A single APK for Wear "
                            + "and non-Wear devices is not supported.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(
                            WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE));

    @Override
    public void beforeCheckFile(Context context) {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(NODE_USES_FEATURE);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (!HARDWARE_TYPE_WATCH.equals(name)) {
            return;
        }

        String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
        if ("false".equals(required)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "`android.hardware.type.watch` must not use `android:required=\"false\"`. "
                            + "A single APK for Wear and non-Wear devices is not supported.");
        }
    }

    @Override
    public void afterCheckFile(Context context) {
    }
}