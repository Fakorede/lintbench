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

    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";
    private static final String ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android";
    private static final String WEAR_FEATURE = "android.hardware.type.watch";

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
                            + "for details.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        // Nothing to do before checking the file
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES_FEATURE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check if this uses-feature element is for android.hardware.type.watch
        String featureName = element.getAttributeNS(ANDROID_NAMESPACE, ATTR_NAME);
        if (!WEAR_FEATURE.equals(featureName)) {
            return;
        }

        // Check if android:required="false" is set
        if (element.hasAttributeNS(ANDROID_NAMESPACE, ATTR_REQUIRED)) {
            String requiredValue = element.getAttributeNS(ANDROID_NAMESPACE, ATTR_REQUIRED);
            if ("false".equals(requiredValue)) {
                Attr requiredAttr = element.getAttributeNodeNS(ANDROID_NAMESPACE, ATTR_REQUIRED);
                context.report(
                        ISSUE,
                        element,
                        requiredAttr != null
                                ? context.getLocation(requiredAttr)
                                : context.getLocation(element),
                        "A `uses-feature` element for `android.hardware.type.watch` must not "
                                + "have `android:required=\"false\"`. A single APK for Wear and "
                                + "non-Wear devices is not supported.");
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        // Nothing to do after checking the file
    }
}