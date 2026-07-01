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

public class WearStandaloneAppDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidWearFeatureAttribute",
                    "Invalid attribute for Wear uses-feature",
                    "For the `android.hardware.type.watch` uses-feature, `android:required=\"false\"` "
                            + "is disallowed. A single APK for Wear and non-Wear devices is not supported; "
                            + "Wear apps must be built as a standalone APK.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE));

    @Override
    public void beforeCheckFile(Context context) {
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList(
                com.android.xml.AndroidManifest.NODE_USES_FEATURE);
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        String name =
                element.getAttributeNS(
                        com.android.SdkConstants.ANDROID_URI,
                        com.android.SdkConstants.ATTR_NAME);
        if ("android.hardware.type.watch".equals(name)) {
            String required =
                    element.getAttributeNS(
                            com.android.SdkConstants.ANDROID_URI,
                            com.android.SdkConstants.ATTR_REQUIRED);
            if ("false".equalsIgnoreCase(required)) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Wear apps must use `android:required=\"true\"` for "
                                + "`android.hardware.type.watch`; a single APK for Wear and "
                                + "non-Wear devices is not supported.");
            }
        }
    }

    @Override
    public void afterCheckFile(Context context) {
    }
}