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
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import java.util.Collection;
import java.util.Collections;

public class WearStandaloneAppDetector extends Detector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";
    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String WATCH_FEATURE = "android.hardware.type.watch";

    public static final Issue ISSUE = Issue.create(
            "InvalidWearFeatureAttribute",
            "Invalid attribute for Wear uses-feature",
            "For the `android.hardware.type.watch` uses-feature, android:required=\"false\" is disallowed. " +
            "A single APK for Wear and non-Wear devices is not supported.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE));

    @Override
    public void beforeCheckFile(@NonNull Context context) {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES_FEATURE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (WATCH_FEATURE.equals(name)) {
            String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
            if ("false".equals(required)) {
                Attr requiredAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_REQUIRED);
                context.report(ISSUE, requiredAttr, context.getLocation(requiredAttr),
                        "For the android.hardware.type.watch uses-feature, android:required=\"false\" is disallowed. " +
                        "A single APK for Wear and non-Wear devices is not supported.");
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
    }
}