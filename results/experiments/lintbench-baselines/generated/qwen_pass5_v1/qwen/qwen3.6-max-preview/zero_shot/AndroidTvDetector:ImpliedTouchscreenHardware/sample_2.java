package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Element;
import java.util.Collection;
import java.util.Collections;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ImpliedTouchscreenHardware",
            "Touchscreen not optional",
            "Apps require the `android.hardware.touchscreen` feature by default. If you want your app to be available on TV, you must also explicitly declare that a touchscreen is not required as follows:\n" +
            "`<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\"/>`",
            Category.USABILITY,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_MANIFEST);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        boolean hasOptionalTouchscreen = false;
        for (Element child : LintUtils.getChildren(element)) {
            if (SdkConstants.TAG_USES_FEATURE.equals(child.getTagName())) {
                String name = child.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                String required = child.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_REQUIRED);
                if ("android.hardware.touchscreen".equals(name) && "false".equals(required)) {
                    hasOptionalTouchscreen = true;
                    break;
                }
            }
        }

        if (!hasOptionalTouchscreen) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Apps require the `android.hardware.touchscreen` feature by default. " +
                    "If you want your app to be available on TV, you must also explicitly declare " +
                    "that a touchscreen is not required.");
        }
    }
}