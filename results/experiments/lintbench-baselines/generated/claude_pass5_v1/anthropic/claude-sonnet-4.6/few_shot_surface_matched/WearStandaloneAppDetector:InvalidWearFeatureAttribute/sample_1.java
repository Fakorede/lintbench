package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_REQUIRED;
import static com.android.xml.AndroidManifest.NODE_USES_FEATURE;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;

public class WearStandaloneAppDetector extends Detector implements XmlScanner {

    private static final String HARDWARE_TYPE_WATCH = "android.hardware.type.watch";

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidWearFeatureAttribute",
                    "Invalid attribute for Wear uses-feature",
                    "For the `android.hardware.type.watch` uses-feature, `android:required=\"false\"`"
                            + " is disallowed. A single APK for Wear and non-Wear devices is not"
                            + " supported.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE));

    private boolean mFoundWatchFeature;

    @Override
    public void beforeCheckFile(@NonNull com.android.tools.lint.detector.api.Context context) {
        mFoundWatchFeature = false;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(NODE_USES_FEATURE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (!HARDWARE_TYPE_WATCH.equals(name)) {
            return;
        }

        mFoundWatchFeature = true;

        String requiredValue = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
        if ("false".equals(requiredValue)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element.getAttributeNodeNS(ANDROID_URI, ATTR_REQUIRED)),
                    "A `uses-feature` with `android:name=\"android.hardware.type.watch\"` must not"
                            + " have `android:required=\"false\"`. A single APK for Wear and"
                            + " non-Wear devices is not supported.");
        }
    }

    @Override
    public void afterCheckFile(@NonNull com.android.tools.lint.detector.api.Context context) {
        // Nothing to do after checking the file in this detector.
    }
}