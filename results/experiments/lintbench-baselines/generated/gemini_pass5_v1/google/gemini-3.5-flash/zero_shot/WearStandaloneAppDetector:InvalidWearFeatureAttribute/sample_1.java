package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class WearStandaloneAppDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "InvalidWearFeatureAttribute",
            "Invalid attribute for Wear uses-feature",
            "For the `android.hardware.type.watch` uses-feature, `android:required=\"false\"` is disallowed. " +
            "A single APK for Wear and non-Wear devices is not supported.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    WearStandaloneAppDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    ).addMoreInfo("https://developer.android.com/training/wearables/apps/packaging.html");

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_USES_FEATURE);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
        if ("android.hardware.type.watch".equals(name)) {
            Attr requiredAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_REQUIRED);
            if (requiredAttr != null && "false".equals(requiredAttr.getValue())) {
                context.report(
                        ISSUE,
                        requiredAttr,
                        context.getLocation(requiredAttr),
                        "For the `android.hardware.type.watch` uses-feature, `android:required=\"false\"` is disallowed."
                );
            }
        }
    }
}