package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;

public class WearStandaloneAppDetector extends ResourceXmlDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    public static final Issue ISSUE = Issue.create(
            "InvalidWearFeatureAttribute",
            "Invalid attribute for Wear uses-feature",
            "For the `android.hardware.type.watch` uses-feature, `android:required=\"false\"` is disallowed. " +
            "A single APK for Wear and non-Wear devices is not supported.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses-feature");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttributeNS(ANDROID_URI, "name");
        if (!"android.hardware.type.watch".equals(name)) {
            return;
        }

        Attr requiredAttr = element.getAttributeNodeNS(ANDROID_URI, "required");
        if (requiredAttr != null && "false".equalsIgnoreCase(requiredAttr.getValue())) {
            context.report(
                    ISSUE,
                    requiredAttr,
                    context.getLocation(requiredAttr),
                    "For the android.hardware.type.watch uses-feature, android:required=\"false\" is disallowed. " +
                    "A single APK for Wear and non-Wear devices is not supported."
            );
        }
    }
}