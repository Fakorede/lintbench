package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
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

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ImpliedTouchscreenHardware",
                    "Touchscreen not optional",
                    "Apps require the `android.hardware.touchscreen` feature by default. If you want "
                            + "your app to be available on TV, you must also explicitly declare that a "
                            + "touchscreen is not required as follows:\n"
                            + "`<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\"/>`",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private boolean mHasOptionalTouchscreen;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses-feature");
    }

    @Override
    public void beforeCheckFile(@NonNull XmlContext context) {
        mHasOptionalTouchscreen = false;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute("android:name");
        String required = element.getAttribute("android:required");
        if ("android.hardware.touchscreen".equals(name) && "false".equals(required)) {
            mHasOptionalTouchscreen = true;
        }
    }

    @Override
    public void afterCheckFile(@NonNull XmlContext context) {
        if (!mHasOptionalTouchscreen) {
            Element root = context.document.getDocumentElement();
            if (root != null) {
                context.report(
                        ISSUE,
                        context.getLocation(root),
                        "Apps require the android.hardware.touchscreen feature by default. "
                                + "To support Android TV, add <uses-feature android:name=\"android.hardware.touchscreen\" "
                                + "android:required=\"false\"/> to the manifest.");
            }
        }
    }
}