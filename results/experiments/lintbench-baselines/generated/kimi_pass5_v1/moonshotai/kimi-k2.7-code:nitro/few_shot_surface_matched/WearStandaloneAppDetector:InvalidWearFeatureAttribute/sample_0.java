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

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";
    private static final String WEAR_FEATURE = "android.hardware.type.watch";
    private static final String USES_FEATURE = "uses-feature";
    private static final String VALUE_FALSE = "false";

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

    @Override
    public void beforeCheckFile(@NonNull Context context) {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(USES_FEATURE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (!WEAR_FEATURE.equals(name)) {
            return;
        }

        Attr requiredAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_REQUIRED);
        if (requiredAttr != null && VALUE_FALSE.equals(requiredAttr.getValue())) {
            context.report(
                    ISSUE,
                    requiredAttr,
                    context.getLocation(requiredAttr),
                    "The `android:required` attribute must not be \"false\" for the"
                            + " `android.hardware.type.watch` uses-feature. A single APK cannot"
                            + " support both Wear and non-Wear devices.");
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
    }
}