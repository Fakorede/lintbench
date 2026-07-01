package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String TAG_CATEGORY = "category";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";
    private static final String VALUE_TOUCHSCREEN = "android.hardware.touchscreen";
    private static final String VALUE_LEANBACK = "android.intent.category.LEANBACK_LAUNCHER";
    private static final String VALUE_FALSE = "false";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ImpliedTouchscreenHardware",
                    "Touchscreen not optional",
                    "Apps require the `android.hardware.touchscreen` feature by default. If you want your app to be available on TV, you must also explicitly declare that a touchscreen is not required as follows: `<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\"/>`",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private static final Collection<String> APPLICABLE_ELEMENTS =
            Arrays.asList(TAG_USES_FEATURE, TAG_CATEGORY);

    private boolean mHasLeanbackLauncher;
    private boolean mHasTouchscreenOptional;
    private Element mLeanbackCategoryElement;

    @Override
    public Collection<String> getApplicableElements() {
        return APPLICABLE_ELEMENTS;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mHasLeanbackLauncher = false;
        mHasTouchscreenOptional = false;
        mLeanbackCategoryElement = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (TAG_USES_FEATURE.equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (VALUE_TOUCHSCREEN.equals(name)) {
                String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
                if (VALUE_FALSE.equalsIgnoreCase(required)) {
                    mHasTouchscreenOptional = true;
                }
            }
        } else if (TAG_CATEGORY.equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (VALUE_LEANBACK.equals(name)) {
                mHasLeanbackLauncher = true;
                mLeanbackCategoryElement = element;
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mHasLeanbackLauncher && !mHasTouchscreenOptional && context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            Location location = xmlContext.getNameLocation(mLeanbackCategoryElement);
            xmlContext.report(
                    ISSUE,
                    mLeanbackCategoryElement,
                    location,
                    "The manifest declares a Leanback launcher but does not declare that a touchscreen is optional; add `<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\"/>`");
        }
    }
}