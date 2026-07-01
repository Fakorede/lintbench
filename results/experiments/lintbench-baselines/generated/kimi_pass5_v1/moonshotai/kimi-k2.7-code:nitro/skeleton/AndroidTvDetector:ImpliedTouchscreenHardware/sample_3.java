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
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";
    private static final String HARDWARE_TOUCHSCREEN = "android.hardware.touchscreen";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ImpliedTouchscreenHardware",
                    "Touchscreen not optional",
                    "Apps require the `android.hardware.touchscreen` feature by default. "
                            + "If you want your app to be available on TV, you must explicitly "
                            + "declare that a touchscreen is not required using "
                            + "`<uses-feature android:name=\"android.hardware.touchscreen\" "
                            + "android:required=\"false\"/>`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mHasOptionalTouchscreen;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mHasOptionalTouchscreen = false;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_NS, ATTR_NAME);
        String required = element.getAttributeNS(ANDROID_NS, ATTR_REQUIRED);
        if (HARDWARE_TOUCHSCREEN.equals(name) && "false".equals(required)) {
            mHasOptionalTouchscreen = true;
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mHasOptionalTouchscreen || !(context instanceof XmlContext)) {
            return;
        }

        XmlContext xmlContext = (XmlContext) context;
        Element root = xmlContext.getDocument().getDocumentElement();
        if (root == null || !"manifest".equals(root.getTagName())) {
            return;
        }

        xmlContext.report(
                ISSUE,
                root,
                xmlContext.getLocation(root),
                "TV applications must explicitly declare that a touchscreen is not required");
    }
}