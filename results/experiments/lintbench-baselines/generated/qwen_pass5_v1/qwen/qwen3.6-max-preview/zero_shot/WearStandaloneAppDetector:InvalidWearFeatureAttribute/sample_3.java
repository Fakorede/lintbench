package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
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

    public static final Issue ISSUE = Issue.create(
            "InvalidWearFeatureAttribute",
            "Invalid attribute for Wear uses-feature",
            "For the `android.hardware.type.watch` uses-feature, `android:required=\"false\"` is disallowed. " +
            "A single APK for Wear and non-Wear devices is not supported.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses-feature");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Attr nameAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "name");
        if (nameAttr == null) {
            return;
        }

        if (!"android.hardware.type.watch".equals(nameAttr.getValue())) {
            return;
        }

        Attr requiredAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "required");
        if (requiredAttr != null && "false".equals(requiredAttr.getValue())) {
            context.report(ISSUE, requiredAttr, context.getLocation(requiredAttr),
                    "`android:required=\"false\"` is not allowed for `android.hardware.type.watch`");
        }
    }
}