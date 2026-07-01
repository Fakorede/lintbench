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

    private static final String ANDROID_MANIFEST_XML = "AndroidManifest.xml";

    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String TAG_CATEGORY = "category";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTIVITY = "activity";

    private static final String ATTR_NAME = "android:name";
    private static final String ATTR_REQUIRED = "android:required";

    private static final String CATEGORY_LEANBACK_LAUNCHER =
            "android.intent.category.LEANBACK_LAUNCHER";

    private static final String HARDWARE_TOUCHSCREEN = "android.hardware.touchscreen";

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

    /** Whether the manifest targets TV (has LEANBACK_LAUNCHER category) */
    private boolean mIsTargetingTv;

    /** Whether touchscreen is explicitly declared as not required */
    private boolean mHasTouchscreenNotRequired;

    /** The manifest element to report the issue on (if needed) */
    private Element mManifestElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_USES_FEATURE, TAG_CATEGORY, "manifest");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIsTargetingTv = false;
        mHasTouchscreenNotRequired = false;
        mManifestElement = null;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mIsTargetingTv && !mHasTouchscreenNotRequired && mManifestElement != null) {
            XmlContext xmlContext = (XmlContext) context;
            xmlContext.report(
                    ISSUE,
                    mManifestElement,
                    xmlContext.getLocation(mManifestElement),
                    "Hardware feature `android.hardware.touchscreen` not explicitly marked as"
                            + " optional; this is required for TV apps. Use"
                            + " `<uses-feature android:name=\"android.hardware.touchscreen\""
                            + " android:required=\"false\"/>` in your manifest.");
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        if ("manifest".equals(tagName)) {
            mManifestElement = element;
        } else if (TAG_USES_FEATURE.equals(tagName)) {
            String name = element.getAttribute(ATTR_NAME);
            if (HARDWARE_TOUCHSCREEN.equals(name)) {
                String required = element.getAttribute(ATTR_REQUIRED);
                if ("false".equals(required)) {
                    mHasTouchscreenNotRequired = true;
                }
            }
        } else if (TAG_CATEGORY.equals(tagName)) {
            String name = element.getAttribute(ATTR_NAME);
            if (CATEGORY_LEANBACK_LAUNCHER.equals(name)) {
                mIsTargetingTv = true;
            }
        }
    }
}