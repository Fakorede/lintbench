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
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;

public class WearStandaloneAppDetector extends Detector implements Detector.XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "InvalidWearFeatureAttribute",
                    "Invalid attribute for Wear uses-feature",
                    "For the android.hardware.type.watch uses-feature, android:required=\"false\" is disallowed. "
                            + "A single APK for Wear and non-Wear devices is not supported.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        // No setup required
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses-feature");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute("android:name");
        if ("android.hardware.type.watch".equals(name)) {
            String required = element.getAttribute("android:required");
            if ("false".equals(required)) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element.getAttributeNode("android:required")),
                        "For the android.hardware.type.watch uses-feature, android:required=\"false\" is disallowed. "
                                + "A single APK for Wear and non-Wear devices is not supported.");
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        // No cleanup required
    }
}