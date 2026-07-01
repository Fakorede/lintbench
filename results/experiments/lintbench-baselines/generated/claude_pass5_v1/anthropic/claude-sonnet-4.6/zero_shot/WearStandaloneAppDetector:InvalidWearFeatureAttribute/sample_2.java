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

/**
 * Detector that checks for invalid attributes on the android.hardware.type.watch uses-feature
 * element in Android manifests.
 */
public class WearStandaloneAppDetector extends Detector implements XmlScanner {

    public static final Issue INVALID_WEAR_FEATURE_ATTRIBUTE = Issue.create(
            "InvalidWearFeatureAttribute",
            "Invalid attribute for Wear uses-feature",
            "For the `android.hardware.type.watch` uses-feature, `android:required=\"false\"` " +
            "is disallowed. A single APK for Wear and non-Wear devices is not supported.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    WearStandaloneAppDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    ).addMoreInfo("https://developer.android.com/training/wearables/apps/packaging.html");

    private static final String USES_FEATURE = "uses-feature";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";
    private static final String ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android";
    private static final String HARDWARE_TYPE_WATCH = "android.hardware.type.watch";

    /** Constructs a new {@link WearStandaloneAppDetector} */
    public WearStandaloneAppDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(USES_FEATURE);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Check if this is the android.hardware.type.watch uses-feature element
        Attr nameAttr = element.getAttributeNodeNS(ANDROID_NAMESPACE, ATTR_NAME);
        if (nameAttr == null) {
            // Try without namespace
            nameAttr = element.getAttributeNode("android:" + ATTR_NAME);
            if (nameAttr == null) {
                return;
            }
        }

        String nameValue = nameAttr.getValue();
        if (!HARDWARE_TYPE_WATCH.equals(nameValue)) {
            return;
        }

        // Check if android:required="false" is set
        Attr requiredAttr = element.getAttributeNodeNS(ANDROID_NAMESPACE, ATTR_REQUIRED);
        if (requiredAttr == null) {
            return;
        }

        String requiredValue = requiredAttr.getValue();
        if ("false".equals(requiredValue)) {
            context.report(
                    INVALID_WEAR_FEATURE_ATTRIBUTE,
                    element,
                    context.getLocation(requiredAttr),
                    "A `uses-feature` element for `android.hardware.type.watch` " +
                    "must not have `android:required=\"false\"`; a single APK for " +
                    "Wear and non-Wear devices is not supported."
            );
        }
    }
}