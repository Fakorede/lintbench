package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;

public class WearStandaloneAppDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidWearFeatureAttribute",
                    "Invalid Wear Feature Attribute",
                    "For the `android.hardware.type.watch` uses-feature, `android:required=\"false\"`"
                            + " is not allowed. A single APK for Wear and non-Wear devices is not"
                            + " supported.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE));

    private static final String USES_FEATURE = "uses-feature";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";
    private static final String WATCH_HARDWARE_TYPE = "android.hardware.type.watch";
    private static final String VALUE_FALSE = "false";

    @Override
    public void beforeCheckFile(Context context) {
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList(USES_FEATURE);
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (!WATCH_HARDWARE_TYPE.equals(name)) {
            return;
        }

        String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
        if (VALUE_FALSE.equals(required)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "A single APK for Wear and non-Wear devices is not supported; "
                            + "android:required must not be false for android.hardware.type.watch");
        }
    }

    @Override
    public void afterCheckFile(Context context) {
    }
}