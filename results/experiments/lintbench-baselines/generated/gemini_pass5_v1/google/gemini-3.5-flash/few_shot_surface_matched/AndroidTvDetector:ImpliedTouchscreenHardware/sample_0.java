package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.Location;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ImpliedTouchscreenHardware",
                    "Touchscreen not optional",
                    "Apps require the `android.hardware.touchscreen` feature by default. If "
                            + "you want your app to be available on TV, you must also "
                            + "explicitly declare that a touchscreen is not required as "
                            + "follows: `<uses-feature android:name=\"android.hardware.touchscreen\" "
                            + "android:required=\"false\"/>`",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private boolean mHasLeanback;
    private boolean mHasLeanbackLauncher;
    private boolean mHasTouchscreenFalse;
    private Element mManifestElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("manifest", "uses-feature", "category");
    }

    @Override
    public void beforeCheckFile(XmlContext context) {
        mHasLeanback = false;
        mHasLeanbackLauncher = false;
        mHasTouchscreenFalse = false;
        mManifestElement = null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tagName = element.getTagName();
        if ("manifest".equals(tagName)) {
            mManifestElement = element;
        } else if ("uses-feature".equals(tagName)) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if ("android.hardware.touchscreen".equals(name)) {
                String required = element.getAttributeNS("http://schemas.android.com/apk/res/android", "required");
                if ("false".equals(required)) {
                    mHasTouchscreenFalse = true;
                }
            } else if ("android.software.leanback".equals(name)) {
                mHasLeanback = true;
            }
        } else if ("category".equals(tagName)) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                mHasLeanbackLauncher = true;
            }
        }
    }

    @Override
    public void afterCheckFile(XmlContext context) {
        if ((mHasLeanback || mHasLeanbackLauncher) && !mHasTouchscreenFalse) {
            if (mManifestElement != null) {
                context.report(
                        ISSUE,
                        mManifestElement,
                        context.getLocation(mManifestElement),
                        "An activity is supportable on TV, but does not declare that touchscreen is not required");
            }
        }
    }
}