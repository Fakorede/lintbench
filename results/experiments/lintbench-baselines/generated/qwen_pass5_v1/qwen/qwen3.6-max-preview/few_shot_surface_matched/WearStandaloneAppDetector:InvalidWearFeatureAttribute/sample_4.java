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
import org.w3c.dom.Element;
import java.util.Collection;
import java.util.Collections;

public class WearStandaloneAppDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "InvalidWearFeatureAttribute",
            "Invalid attribute for Wear uses-feature",
            "For the android.hardware.type.watch uses-feature, android:required=\"false\" is disallowed. " +
            "A single APK for Wear and non-Wear devices is not supported.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE));

    @Override
    public void beforeCheckFile(@NonNull Context context) {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses-feature");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute("android:name");
        String required = element.getAttribute("android:required");
        if ("android.hardware.type.watch".equals(name) && "false".equals(required)) {
            context.report(ISSUE, element, context.getLocation(element),
                    "android:required=\"false\" is disallowed for android.hardware.type.watch");
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
    }
}