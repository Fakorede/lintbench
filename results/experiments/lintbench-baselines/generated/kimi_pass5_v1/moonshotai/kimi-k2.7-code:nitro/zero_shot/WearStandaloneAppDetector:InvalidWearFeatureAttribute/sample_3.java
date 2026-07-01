package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_HARDWARE_TYPE_WATCH;
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

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

public class WearStandaloneAppDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "InvalidWearFeatureAttribute",
            "Invalid required attribute for Wear uses-feature",
            "For the `android.hardware.type.watch` uses-feature, `android:required=\"false\"` "
                    + "is not allowed. A single APK for both Wear and non-Wear devices is not "
                    + "supported.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE))
            .addMoreInfo("https://developer.android.com/training/wearables/apps/packaging.html");

    private static final String USES_FEATURE = "uses-feature";

    @Override
    @NonNull
    public Collection<String> getApplicableElements() {
        return Arrays.asList(USES_FEATURE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!USES_FEATURE.equals(element.getTagName())) {
            return;
        }

        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (!ANDROID_HARDWARE_TYPE_WATCH.equals(name)) {
            return;
        }

        Attr required = element.getAttributeNodeNS(ANDROID_URI, ATTR_REQUIRED);
        if (required != null && VALUE_FALSE.equals(required.getValue())) {
            context.report(ISSUE, required, context.getValueLocation(required),
                    "Use of `android:required=\"false\"` with `android.hardware.type.watch` is "
                            + "not allowed; Wear and non-Wear devices must use separate APKs.");
        }
    }
}