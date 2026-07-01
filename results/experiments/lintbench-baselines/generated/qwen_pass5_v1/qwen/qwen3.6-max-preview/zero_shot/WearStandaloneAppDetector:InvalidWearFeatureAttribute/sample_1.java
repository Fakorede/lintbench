package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.Collections;

public class WearStandaloneAppDetector extends Detector implements XmlScanner {

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
        return Collections.singletonList(SdkConstants.TAG_USES_FEATURE);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
        if (!"android.hardware.type.watch".equals(name)) {
            return;
        }

        String required = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_REQUIRED);
        if ("false".equals(required)) {
            Node attrNode = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_REQUIRED);
            Location location = context.getLocation(attrNode != null ? attrNode : element);
            context.report(ISSUE, location,
                "`android:required=\"false\"` is not allowed for `android.hardware.type.watch`");
        }
    }
}