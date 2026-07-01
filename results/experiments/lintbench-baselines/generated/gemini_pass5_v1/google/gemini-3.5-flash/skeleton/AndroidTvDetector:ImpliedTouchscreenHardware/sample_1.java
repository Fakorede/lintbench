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
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ImpliedTouchscreenHardware",
                    "Touchscreen not optional",
                    "Apps require the `android.hardware.touchscreen` feature by default. "
                            + "If you want your app to be available on TV, you must also "
                            + "explicitly declare that a touchscreen is not required as "
                            + "follows: `<uses-feature android:name=\"android.hardware.touchscreen\" "
                            + "android:required=\"false\"/>`",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mHasLeanbackFeature = false;
    private boolean mHasLeanbackLauncher = false;
    private boolean mHasTouchscreenDisabled = false;
    private Element mReportingElement = null;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("uses-feature", "category", "manifest");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mHasLeanbackFeature = false;
        mHasLeanbackLauncher = false;
        mHasTouchscreenDisabled = false;
        mReportingElement = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if ("manifest".equals(tagName)) {
            if (mReportingElement == null) {
                mReportingElement = element;
            }
        } else if ("uses-feature".equals(tagName)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if ("android.software.leanback".equals(name)) {
                mHasLeanbackFeature = true;
                mReportingElement = element;
            } else if ("android.hardware.touchscreen".equals(name)) {
                String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
                if ("false".equals(required)) {
                    mHasTouchscreenDisabled = true;
                }
            }
        } else if ("category".equals(tagName)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                mHasLeanbackLauncher = true;
                if (mReportingElement == null || "manifest".equals(mReportingElement.getTagName())) {
                    mReportingElement = element;
                }
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if ((mHasLeanbackFeature || mHasLeanbackLauncher) && !mHasTouchscreenDisabled) {
            if (context instanceof XmlContext && mReportingElement != null) {
                XmlContext xmlContext = (XmlContext) context;
                xmlContext.report(
                        ISSUE,
                        mReportingElement,
                        xmlContext.getLocation(mReportingElement),
                        "Hardware feature `android.hardware.touchscreen` should be "
                                + "explicitly marked as optional (`required=\"false\"`) "
                                + "for television devices.");
            }
        }
    }
}