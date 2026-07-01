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
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String TAG_CATEGORY = "category";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";
    private static final String VALUE_TRUE = "true";
    private static final String VALUE_FALSE = "false";

    private static final String FEATURE_TOUCHSCREEN = "android.hardware.touchscreen";
    private static final String FEATURE_LEANBACK = "android.software.leanback";
    private static final String CATEGORY_LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER";

    public static final Issue ISSUE = Issue.create(
            "ImpliedTouchscreenHardware",
            "Touchscreen not optional",
            "Apps require the `android.hardware.touchscreen` feature by default. If you want your app to be available on TV, you must also explicitly declare that a touchscreen is not required as follows: `<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\"/>`",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    private boolean mHasTvFeatureOrCategory;
    private boolean mTouchscreenNotRequired;
    private Location mTvLocation;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mHasTvFeatureOrCategory = false;
        mTouchscreenNotRequired = false;
        mTvLocation = null;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_USES_FEATURE, TAG_CATEGORY);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        String name = getAttributeValue(element, ATTR_NAME);

        if (TAG_USES_FEATURE.equals(tag)) {
            if (FEATURE_TOUCHSCREEN.equals(name)) {
                String required = getAttributeValue(element, ATTR_REQUIRED);
                if (VALUE_FALSE.equals(required)) {
                    mTouchscreenNotRequired = true;
                }
            } else if (FEATURE_LEANBACK.equals(name)) {
                String required = getAttributeValue(element, ATTR_REQUIRED);
                if (!VALUE_FALSE.equals(required)) {
                    mHasTvFeatureOrCategory = true;
                    mTvLocation = context.getLocation(element);
                }
            }
        } else if (TAG_CATEGORY.equals(tag) && CATEGORY_LEANBACK_LAUNCHER.equals(name)) {
            mHasTvFeatureOrCategory = true;
            mTvLocation = context.getLocation(element);
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mHasTvFeatureOrCategory && !mTouchscreenNotRequired) {
            context.report(
                    ISSUE,
                    mTvLocation,
                    "TV apps must explicitly declare that a touchscreen is not required with `android:required=\"false\"`"
            );
        }
    }

    private static String getAttributeValue(@NonNull Element element, @NonNull String localName) {
        String value = element.getAttribute(localName);
        if (value == null || value.isEmpty()) {
            value = element.getAttribute("android:" + localName);
        }
        return value;
    }
}