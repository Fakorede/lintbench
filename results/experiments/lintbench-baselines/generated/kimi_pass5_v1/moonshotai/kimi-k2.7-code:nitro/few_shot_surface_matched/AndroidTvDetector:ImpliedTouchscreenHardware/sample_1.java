package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_REQUIRED;
import static com.android.xml.AndroidManifest.NODE_USES_FEATURE;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
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

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ImpliedTouchscreenHardware",
                    "Touchscreen hardware is implicitly required",
                    "Apps require the `android.hardware.touchscreen` feature by default. If you "
                            + "want your app to be available on TV, you must explicitly declare "
                            + "that a touchscreen is not required using "
                            + "`<uses-feature android:name=\"android.hardware.touchscreen\" "
                            + "android:required=\"false\"/>`.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final String TOUCHSCREEN_HARDWARE = "android.hardware.touchscreen";

    private boolean mDeclaredOptionalTouchscreen;
    private boolean mHasTouchscreenDeclaration;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(NODE_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(@NonNull XmlContext context) {
        mDeclaredOptionalTouchscreen = false;
        mHasTouchscreenDeclaration = false;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (!TOUCHSCREEN_HARDWARE.equals(name)) {
            return;
        }

        mHasTouchscreenDeclaration = true;
        String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
        if ("false".equals(required)) {
            mDeclaredOptionalTouchscreen = true;
        } else {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Touchscreen hardware is implicitly required; for TV support declare it "
                            + "with `android:required=\"false\"`");
        }
    }

    @Override
    public void afterCheckFile(@NonNull XmlContext context) {
        if (!mDeclaredOptionalTouchscreen && !mHasTouchscreenDeclaration) {
            Element root = context.document.getDocumentElement();
            context.report(
                    ISSUE,
                    root,
                    context.getLocation(root),
                    "TV apps must explicitly declare that a touchscreen is not required: "
                            + "`<uses-feature android:name=\"android.hardware.touchscreen\" "
                            + "android:required=\"false\"/>`");
        }
    }
}