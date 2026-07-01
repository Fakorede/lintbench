package com.android.tools.lint.checks;

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

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String HARDWARE_TOUCHSCREEN = "android.hardware.touchscreen";
    private static final String SOFTWARE_LEANBACK = "android.software.leanback";
    private static final String CATEGORY_LEANBACK_LAUNCHER =
            "android.intent.category.LEANBACK_LAUNCHER";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ImpliedTouchscreenHardware",
                    "Touchscreen not optional",
                    "Apps require the `android.hardware.touchscreen` feature by default. If you want "
                            + "your app to be available on TV, you must also explicitly declare "
                            + "that a touchscreen is not required using "
                            + "`<uses-feature android:name=\"android.hardware.touchscreen\" "
                            + "android:required=\"false\"/>`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mHasLeanback;
    private boolean mHasLeanbackLauncher;
    private boolean mHasOptionalTouchscreen;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("uses-feature", "category");
    }

    @Override
    public void beforeCheckFile(Context context) {
        mHasLeanback = false;
        mHasLeanbackLauncher = false;
        mHasOptionalTouchscreen = false;
    }

    @Override
    public void afterCheckFile(Context context) {
        if (!mHasLeanback && !mHasLeanbackLauncher) {
            return;
        }

        if (mHasOptionalTouchscreen) {
            return;
        }

        Element manifest = ((XmlContext) context).document.getDocumentElement();
        ((XmlContext) context).report(
                ISSUE,
                manifest,
                ((XmlContext) context).getLocation(manifest),
                "If your app is intended for TV, you must declare that a touchscreen is not "
                        + "required with "
                        + "`<uses-feature android:name=\"android.hardware.touchscreen\" "
                        + "android:required=\"false\"/>`.");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        if ("uses-feature".equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, "name");
            if (HARDWARE_TOUCHSCREEN.equals(name)) {
                String required = element.getAttributeNS(ANDROID_URI, "required");
                if ("false".equals(required)) {
                    mHasOptionalTouchscreen = true;
                }
            } else if (SOFTWARE_LEANBACK.equals(name)) {
                mHasLeanback = true;
            }
        } else if ("category".equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, "name");
            if (CATEGORY_LEANBACK_LAUNCHER.equals(name)) {
                mHasLeanbackLauncher = true;
            }
        }
    }
}