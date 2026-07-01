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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_REQUIRED;
import static com.android.xml.AndroidManifest.NODE_USES_FEATURE;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ImpliedTouchscreenHardware",
                    "Touchscreen not optional",
                    "Apps require the `android.hardware.touchscreen` feature by default. If you want "
                            + "your app to be available on TV, you must also explicitly declare that a "
                            + "touchscreen is not required as follows: "
                            + "`<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\"/>`",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            AndroidTvDetector.class,
                            Scope.MANIFEST_SCOPE));

    private boolean hasOptionalTouchscreen;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(NODE_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(@NonNull XmlContext context) {
        hasOptionalTouchscreen = false;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
        if ("android.hardware.touchscreen".equals(name) && "false".equals(required)) {
            hasOptionalTouchscreen = true;
        }
    }

    @Override
    public void afterCheckFile(@NonNull XmlContext context) {
        if (!hasOptionalTouchscreen) {
            Element root = context.getDocument().getDocumentElement();
            if (root != null) {
                context.report(
                        ISSUE,
                        root,
                        context.getLocation(root),
                        "Touchscreen hardware feature is required by default. Declare it as not required to support Android TV.");
            }
        }
    }
}