package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ImpliedTouchscreenHardware",
            "Touchscreen hardware required by default",
            "Apps require the `android.hardware.touchscreen` feature by default. If you want "
                    + "your app to be available on TV, you must also explicitly declare that a "
                    + "touchscreen is not required as follows: "
                    + "`<uses-feature android:name=\"android.hardware.touchscreen\" "
                    + "android:required=\"false\"/>`",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private boolean mHasOptionalTouchscreen;

    @Override
    public void beforeVisitRoot(@NonNull XmlContext context, @NonNull Element root) {
        mHasOptionalTouchscreen = false;
    }

    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_USES_FEATURE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
        if (!"android.hardware.touchscreen".equals(name)) {
            return;
        }

        String required = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_REQUIRED);
        if ("false".equalsIgnoreCase(required)) {
            mHasOptionalTouchscreen = true;
        }
    }

    @Override
    public void afterVisitRoot(@NonNull XmlContext context, @NonNull Element root) {
        if (!mHasOptionalTouchscreen) {
            context.report(
                    ISSUE,
                    root,
                    context.getLocation(root),
                    "Touchscreen hardware is required by default; declare "
                            + "`<uses-feature android:name=\"android.hardware.touchscreen\" "
                            + "android:required=\"false\"/>` to support TV devices");
        }
    }
}