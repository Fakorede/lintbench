package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

public class WearStandaloneAppDetector extends Detector implements XmlScanner {

    public static final Issue INVALID_WEAR_FEATURE_ATTRIBUTE = Issue.create(
            "InvalidWearFeatureAttribute",
            "Invalid attribute for Wear uses-feature",
            "For the `android.hardware.type.watch` uses-feature, `android:required=\"false\"` " +
            "is disallowed. A single APK for Wear and non-Wear devices is not supported.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(
                    WearStandaloneAppDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    ).addMoreInfo("https://developer.android.com/training/wearables/apps/packaging.html");

    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String WEAR_FEATURE = "android.hardware.type.watch";

    public WearStandaloneAppDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_USES_FEATURE);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Attr nameAttr = element.getAttributeNodeNS(ANDROID_NS, ATTR_NAME);
        if (nameAttr == null) {
            nameAttr = element.getAttributeNode(ATTR_NAME);
        }
        if (nameAttr == null || !WEAR_FEATURE.equals(nameAttr.getValue())) {
            return;
        }

        Attr requiredAttr = element.getAttributeNodeNS(ANDROID_NS, ATTR_REQUIRED);
        if (requiredAttr == null) {
            requiredAttr = element.getAttributeNode(ATTR_REQUIRED);
        }
        if (requiredAttr != null && "false".equals(requiredAttr.getValue())) {
            context.report(
                    INVALID_WEAR_FEATURE_ATTRIBUTE,
                    element,
                    context.getLocation(requiredAttr),
                    "Wear app entry point `" + WEAR_FEATURE + "` should not have " +
                    "`android:required=\"false\"`; a single APK for Wear and non-Wear " +
                    "devices is not supported."
            );
        }
    }
}