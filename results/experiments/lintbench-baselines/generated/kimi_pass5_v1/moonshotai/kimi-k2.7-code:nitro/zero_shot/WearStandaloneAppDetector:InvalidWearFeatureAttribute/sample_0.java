package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;

public class WearStandaloneAppDetector extends ResourceXmlDetector {

    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String FEATURE_WATCH = "android.hardware.type.watch";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";

    private static final String MESSAGE =
            "Single APK for Wear and non-Wear devices is not supported. The "
                    + "`android.hardware.type.watch` `<uses-feature>` must not specify "
                    + "`android:required=\"false\"`.";

    public static final Issue ISSUE = Issue.create(
            "InvalidWearFeatureAttribute",
            "Invalid attribute for Wear uses-feature",
            "The `android.hardware.type.watch` `<uses-feature>` must not set "
                    + "`android:required=\"false\"`. A single APK for Wear and non-Wear devices "
                    + "is not supported.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES_FEATURE);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!FEATURE_WATCH.equals(
                element.getAttributeNS(SdkConstants.ANDROID_URI, ATTR_NAME))) {
            return;
        }

        Attr required = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, ATTR_REQUIRED);
        if (required != null && required.getValue().equalsIgnoreCase("false")) {
            context.report(ISSUE, required, context.getLocation(required), MESSAGE);
        }
    }
}