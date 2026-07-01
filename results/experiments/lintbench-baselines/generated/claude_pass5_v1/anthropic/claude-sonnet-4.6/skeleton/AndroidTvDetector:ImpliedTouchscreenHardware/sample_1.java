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
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    private static final String USES_FEATURE = "uses-feature";
    private static final String USES_INTENT = "uses-intent"; // Not used but kept for context
    private static final String ATTR_NAME = "android:name";
    private static final String ATTR_REQUIRED = "android:required";
    private static final String HARDWARE_TOUCHSCREEN = "android.hardware.touchscreen";
    private static final String LEANBACK_FEATURE = "android.software.leanback";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ImpliedTouchscreenHardware",
                    "Touchscreen not optional",
                    "Apps require the `android.hardware.touchscreen` feature by default. If "
                            + "you want your app to be available on TV, you must also "
                            + "explicitly declare that a touchscreen is not required as "
                            + "follows:\n"
                            + "`<uses-feature android:name=\"android.hardware.touchscreen\" "
                            + "android:required=\"false\"/>`",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    /** Whether the manifest declares android.software.leanback as a used feature */
    private boolean mUsesLeanbackFeature;

    /** Whether the manifest explicitly declares android.hardware.touchscreen as not required */
    private boolean mExplicitlyDeclaresTouchscreenNotRequired;

    /** The element representing the leanback uses-feature declaration, for error reporting */
    private Element mLeanbackElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mUsesLeanbackFeature = false;
        mExplicitlyDeclaresTouchscreenNotRequired = false;
        mLeanbackElement = null;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mUsesLeanbackFeature && !mExplicitlyDeclaresTouchscreenNotRequired) {
            if (context instanceof XmlContext && mLeanbackElement != null) {
                XmlContext xmlContext = (XmlContext) context;
                xmlContext.report(
                        ISSUE,
                        mLeanbackElement,
                        xmlContext.getLocation(mLeanbackElement),
                        "Touchscreen feature not explicitly marked as optional");
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute(ATTR_NAME);
        if (name == null) {
            return;
        }

        if (LEANBACK_FEATURE.equals(name)) {
            mUsesLeanbackFeature = true;
            mLeanbackElement = element;
        } else if (HARDWARE_TOUCHSCREEN.equals(name)) {
            String required = element.getAttribute(ATTR_REQUIRED);
            if ("false".equals(required)) {
                mExplicitlyDeclaresTouchscreenNotRequired = true;
            }
        }
    }
}