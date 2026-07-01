package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.google.common.collect.Sets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import org.w3c.dom.Element;

public class WearStandaloneAppDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "InvalidWearFeatureAttribute",
            "Invalid attribute for Wear uses-feature",
            "For the `android.hardware.type.watch` uses-feature, `android:required=\"false\"` is disallowed. "
                    + "A single APK for Wear and non-Wear devices is not supported.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    WearStandaloneAppDetector.class,
                    Scope.MANIFEST_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_USES_FEATURE);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttribute(SdkConstants.ATTR_NAME);
        if (SdkConstants.ANDROID_HARDWARE_TYPE_WATCH.equals(name)) {
            String required = element.getAttribute(SdkConstants.ATTR_REQUIRED);
            if (SdkConstants.VALUE_FALSE.equals(required)) {
                context.report(ISSUE, context.getLocation(element),
                        "A single APK for Wear and non-Wear devices is not supported. "
                                + "Do not set `android:required=\"false\"` on the "
                                + "`android.hardware.type.watch` uses-feature.");
            }
        }
    }
}