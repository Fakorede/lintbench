package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_REQUIRED;
import static com.android.SdkConstants.VALUE_FALSE;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
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

    @Override
    @NonNull
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses-feature");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeValue(ATTR_NAME, ANDROID_URI);
        if ("android.hardware.type.watch".equals(name)) {
            String required = element.getAttributeValue(ATTR_REQUIRED, ANDROID_URI);
            if (VALUE_FALSE.equals(required)) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Wear apps must not set android:required=\"false\" on the "
                                + "android.hardware.type.watch uses-feature");
            }
        }
    }

    private static final String ID = "InvalidWearFeatureAttribute";
    private static final String DESCRIPTION = "Invalid attribute for Wear uses-feature";
    private static final String EXPLANATION =
            "A single APK for Wear and non-Wear devices is not supported. Therefore, the "
                    + "`android.hardware.type.watch` uses-feature must not set "
                    + "`android:required=\"false\"`.";
    private static final Category CATEGORY = Category.CORRECTNESS;
    private static final int PRIORITY = 6;
    private static final Severity SEVERITY = Severity.ERROR;

    public static final Issue ISSUE =
            Issue.create(
                    ID,
                    DESCRIPTION,
                    EXPLANATION,
                    CATEGORY,
                    PRIORITY,
                    SEVERITY,
                    new Implementation(WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE));
}