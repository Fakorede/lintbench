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
 * Detector for invalid Wear uses-feature attributes.
 *
 * <p>Checks that {@code android.hardware.type.watch} uses-feature does not have
 * {@code android:required="false"}, since a single APK for Wear and non-Wear devices
 * is not supported.
 */
public class WearStandaloneAppDetector extends Detector implements XmlScanner {

    private static final String ANDROID_HARDWARE_TYPE_WATCH = "android.hardware.type.watch";
    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

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

    /** Constructs a new {@link WearStandaloneAppDetector}. */
    public WearStandaloneAppDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_USES_FEATURE);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Check if this is the android.hardware.type.watch uses-feature element
        Attr nameAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
        if (nameAttr == null) {
            // Try without namespace
            nameAttr = element.getAttributeNode("android:" + ATTR_NAME);
            if (nameAttr == null) {
                return;
            }
        }

        String nameValue = nameAttr.getValue();
        if (!ANDROID_HARDWARE_TYPE_WATCH.equals(nameValue)) {
            return;
        }

        // Check for android:required="false"
        Attr requiredAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_REQUIRED);
        if (requiredAttr == null) {
            return;
        }

        String requiredValue = requiredAttr.getValue();
        if ("false".equals(requiredValue)) {
            context.report(
                    INVALID_WEAR_FEATURE_ATTRIBUTE,
                    requiredAttr,
                    context.getValueLocation(requiredAttr),
                    "A single APK for Wear and non-Wear devices is not supported. " +
                    "`android:required=\"false\"` is disallowed for " +
                    "`android.hardware.type.watch` uses-feature."
            );
        }
    }
}