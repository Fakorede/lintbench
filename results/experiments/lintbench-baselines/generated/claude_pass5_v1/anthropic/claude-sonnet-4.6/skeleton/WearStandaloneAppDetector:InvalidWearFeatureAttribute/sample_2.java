package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
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

    private static final String ANDROID_HARDWARE_TYPE_WATCH = "android.hardware.type.watch";
    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    private static final Implementation IMPLEMENTATION =
            new Implementation(WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidWearFeatureAttribute",
                    "Invalid attribute for Wear uses-feature",
                    "For the `android.hardware.type.watch` uses-feature, `android:required=\"false\"` "
                            + "is disallowed. A single APK for Wear and non-Wear devices is not supported.\n"
                            + "\n"
                            + "See https://developer.android.com/training/wearables/apps/packaging.html "
                            + "for more information.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        // Nothing to initialize before checking the file
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES_FEATURE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check if this uses-feature is for android.hardware.type.watch
        String featureName = element.getAttributeNS(ANDROID_NS, ATTR_NAME);
        if (featureName == null || featureName.isEmpty()) {
            // Try without namespace
            featureName = element.getAttribute("android:" + ATTR_NAME);
        }

        if (!ANDROID_HARDWARE_TYPE_WATCH.equals(featureName)) {
            return;
        }

        // Check if android:required="false" is set
        Attr requiredAttr = element.getAttributeNodeNS(ANDROID_NS, ATTR_REQUIRED);
        if (requiredAttr == null) {
            return;
        }

        String requiredValue = requiredAttr.getValue();
        if ("false".equals(requiredValue)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(requiredAttr),
                    "Attribute `android:required=\"false\"` is not allowed for the feature "
                            + "`android.hardware.type.watch`. A single APK for Wear and "
                            + "non-Wear devices is not supported.");
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        // Nothing to do after checking the file
    }
}