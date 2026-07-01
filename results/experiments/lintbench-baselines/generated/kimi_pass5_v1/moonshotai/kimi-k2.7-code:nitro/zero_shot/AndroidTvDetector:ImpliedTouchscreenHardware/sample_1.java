package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SdkConstants;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    public static final Issue IMPLIED_TOUCHSCREEN_HARDWARE = Issue.create(
            "ImpliedTouchscreenHardware",
            "Touchscreen not optional",
            "Apps require the `android.hardware.touchscreen` feature by default. If you want "
                    + "your app to be available on TV, you must also explicitly declare that a "
                    + "touchscreen is not required as follows:\n\n"
                    + "`<uses-feature android:name=\"android.hardware.touchscreen\" "
                    + "android:required=\"false\"/>`\n\n"
                    + "Reference: https://developer.android.com/guide/topics/manifest/uses-feature-element.html",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final String TOUCHSCREEN_HARDWARE = "android.hardware.touchscreen";
    private static final String LEANBACK_FEATURE = "android.software.leanback";
    private static final String TELEVISION_FEATURE = "android.hardware.type.television";
    private static final String LEANBACK_LAUNCHER_CATEGORY =
            "android.intent.category.LEANBACK_LAUNCHER";

    private boolean mIsTvApp;
    private boolean mDeclaredTouchscreenOptional;
    private Element mManifestElement;
    private XmlContext mContext;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                SdkConstants.TAG_MANIFEST,
                SdkConstants.TAG_APPLICATION,
                SdkConstants.TAG_USES_FEATURE,
                SdkConstants.TAG_CATEGORY);
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mIsTvApp = false;
        mDeclaredTouchscreenOptional = false;
        mManifestElement = null;
        mContext = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        mContext = context;

        String tag = element.getTagName();
        if (SdkConstants.TAG_MANIFEST.equals(tag)) {
            mManifestElement = element;
        } else if (SdkConstants.TAG_USES_FEATURE.equals(tag)) {
            String name =
                    element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if (TOUCHSCREEN_HARDWARE.equals(name)) {
                String required =
                        element.getAttributeNS(
                                SdkConstants.ANDROID_URI, SdkConstants.ATTR_REQUIRED);
                if ("false".equalsIgnoreCase(required)) {
                    mDeclaredTouchscreenOptional = true;
                }
            } else if (LEANBACK_FEATURE.equals(name) || TELEVISION_FEATURE.equals(name)) {
                mIsTvApp = true;
            }
        } else if (SdkConstants.TAG_APPLICATION.equals(tag)) {
            if (element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_BANNER)) {
                mIsTvApp = true;
            }
        } else if (SdkConstants.TAG_CATEGORY.equals(tag)) {
            String name =
                    element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if (LEANBACK_LAUNCHER_CATEGORY.equals(name)) {
                mIsTvApp = true;
            }
        }
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        if (mIsTvApp
                && !mDeclaredTouchscreenOptional
                && mManifestElement != null
                && mContext != null) {
            mContext.report(
                    IMPLIED_TOUCHSCREEN_HARDWARE,
                    mManifestElement,
                    mContext.getLocation(mManifestElement),
                    "TV applications must declare that a touchscreen is not required; add "
                            + "`<uses-feature android:name=\"android.hardware.touchscreen\" "
                            + "android:required=\"false\"/>`");
        }
    }
}