package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_REQUIRED;
import static com.android.SdkConstants.TAG_CATEGORY;
import static com.android.SdkConstants.TAG_USES_FEATURE;

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
    public static final Issue ISSUE = Issue.create(
            "ImpliedTouchscreenHardware",
            "Touchscreen not optional",
            "Apps require the `android.hardware.touchscreen` feature by default. If you want your app to be available on TV, you must also explicitly declare that a touchscreen is not required as follows:\n`<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\"/>`",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE))
            .addMoreInfo("https://developer.android.com/guide/topics/manifest/uses-feature-element.html");

    private static final String TOUCHSCREEN_HARDWARE = "android.hardware.touchscreen";
    private static final String LEANBACK_SOFTWARE = "android.software.leanback";
    private static final String LEANBACK_LAUNCHER_CATEGORY = "android.intent.category.LEANBACK_LAUNCHER";

    private Element mLeanbackElement;
    private boolean mTouchscreenNotRequired;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_USES_FEATURE, TAG_CATEGORY);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mLeanbackElement = null;
        mTouchscreenNotRequired = false;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (TAG_USES_FEATURE.equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (LEANBACK_SOFTWARE.equals(name)) {
                mLeanbackElement = element;
            } else if (TOUCHSCREEN_HARDWARE.equals(name)) {
                String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
                if ("false".equals(required)) {
                    mTouchscreenNotRequired = true;
                }
            }
        } else if (TAG_CATEGORY.equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (LEANBACK_LAUNCHER_CATEGORY.equals(name)) {
                mLeanbackElement = element;
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mLeanbackElement != null && !mTouchscreenNotRequired) {
            XmlContext xmlContext = (XmlContext) context;
            xmlContext.report(ISSUE, mLeanbackElement, xmlContext.getLocation(mLeanbackElement),
                    "TV apps must explicitly declare that a touchscreen is not required");
        }
    }
}