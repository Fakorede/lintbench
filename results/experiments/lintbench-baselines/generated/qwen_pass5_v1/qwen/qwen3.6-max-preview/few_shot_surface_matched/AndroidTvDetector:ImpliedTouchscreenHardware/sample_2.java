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
import java.util.Arrays;
import java.util.Collection;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ImpliedTouchscreenHardware",
            "Touchscreen not optional",
            "Apps require the `android.hardware.touchscreen` feature by default. If you want your app to be available on TV, you must also explicitly declare that a touchscreen is not required as follows:\n" +
            "`<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\"/>`",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private boolean hasOptionalTouchscreen;
    private Element manifestElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("manifest", "uses-feature");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        hasOptionalTouchscreen = false;
        manifestElement = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if ("manifest".equals(tag)) {
            manifestElement = element;
        } else if ("uses-feature".equals(tag)) {
            String name = element.getAttribute("android:name");
            String required = element.getAttribute("android:required");
            if ("android.hardware.touchscreen".equals(name) && "false".equals(required)) {
                hasOptionalTouchscreen = true;
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!hasOptionalTouchscreen && manifestElement != null) {
            XmlContext xmlContext = (XmlContext) context;
            xmlContext.report(ISSUE, manifestElement, xmlContext.getLocation(manifestElement),
                    "Apps require the android.hardware.touchscreen feature by default. " +
                    "If you want your app to be available on TV, you must explicitly declare " +
                    "that a touchscreen is not required.");
        }
    }
}