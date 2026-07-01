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

import java.util.Collection;
import java.util.Collections;

/**
 * Detector for invalid attributes on the android.hardware.type.watch uses-feature element.
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
            ))
            .addMoreInfo("https://developer.android.com/training/wearables/apps/packaging.html");

    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";
    private static final String ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android";
    private static final String WEAR_FEATURE = "android.hardware.type.watch";

    /** Constructs a new {@link WearStandaloneAppDetector} */
    public WearStandaloneAppDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES_FEATURE);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Check if this is the watch uses-feature element
        Attr nameAttr = element.getAttributeNodeNS(ANDROID_NAMESPACE, ATTR_NAME);
        if (nameAttr == null) {
            // Try without namespace
            nameAttr = element.getAttributeNode("android:name");
        }

        if (nameAttr == null) {
            return;
        }

        String featureName = nameAttr.getValue();
        if (!WEAR_FEATURE.equals(featureName)) {
            return;
        }

        // Check if android:required="false" is set
        Attr requiredAttr = element.getAttributeNodeNS(ANDROID_NAMESPACE, ATTR_REQUIRED);
        if (requiredAttr == null) {
            // Try without namespace
            requiredAttr = element.getAttributeNode("android:required");
        }

        if (requiredAttr != null && "false".equals(requiredAttr.getValue())) {
            context.report(
                    INVALID_WEAR_FEATURE_ATTRIBUTE,
                    element,
                    context.getLocation(requiredAttr),
                    "android:required=\"false\" is not allowed for the `android.hardware.type.watch` " +
                    "feature. A single APK for Wear and non-Wear devices is not supported."
            );
        }
    }
}