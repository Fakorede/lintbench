package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ANDROID_WEAR_FEATURE;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_REQUIRED;
import static com.android.xml.AndroidManifest.NODE_USES_FEATURE;

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
import org.w3c.dom.Element;

public class WearStandaloneAppDetector extends Detector implements XmlScanner {

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
                    new Implementation(WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE));

    private Element mInvalidElement;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mInvalidElement = null;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(NODE_USES_FEATURE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (mInvalidElement != null) {
            return;
        }

        if (!ANDROID_WEAR_FEATURE.equals(element.getAttributeNS(ANDROID_URI, ATTR_NAME))) {
            return;
        }

        String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
        if ("false".equals(required)) {
            mInvalidElement = element;
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mInvalidElement != null && context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            xmlContext.report(
                    ISSUE,
                    mInvalidElement,
                    xmlContext.getLocation(mInvalidElement),
                    "A single APK for Wear and non-Wear devices is not supported. Do not set"
                            + " `android:required=\"false\"` for the `android.hardware.type.watch`"
                            + " uses-feature.");
            mInvalidElement = null;
        }
    }
}