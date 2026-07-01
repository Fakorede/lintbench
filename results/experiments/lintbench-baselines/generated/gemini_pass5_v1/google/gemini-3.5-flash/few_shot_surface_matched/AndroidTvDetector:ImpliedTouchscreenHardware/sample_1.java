package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

public class AndroidTvDetector extends Detector implements XmlScanner {

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
                    new Implementation(
                            AndroidTvDetector.class,
                            Scope.MANIFEST_SCOPE));

    private boolean mHasLeanback;
    private boolean mDeclaresTouchscreen;
    private boolean mTouchscreenRequired;
    private org.w3c.dom.Element mTouchscreenElement;

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Arrays.asList("uses-feature", "category");
    }

    @Override
    public void beforeCheckFile(XmlContext context) {
        mHasLeanback = false;
        mDeclaresTouchscreen = false;
        mTouchscreenRequired = true;
        mTouchscreenElement = null;
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        String tagName = element.getTagName();
        if ("uses-feature".equals(tagName)) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if ("android.software.leanback".equals(name)) {
                mHasLeanback = true;
            } else if ("android.hardware.touchscreen".equals(name)) {
                mDeclaresTouchscreen = true;
                String required = element.getAttributeNS("http://schemas.android.com/apk/res/android", "required");
                if ("false".equals(required)) {
                    mTouchscreenRequired = false;
                } else {
                    mTouchscreenRequired = true;
                    mTouchscreenElement = element;
                }
            }
        } else if ("category".equals(tagName)) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                mHasLeanback = true;
            }
        }
    }

    @Override
    public void afterCheckFile(XmlContext context) {
        if (mHasLeanback) {
            if (!mDeclaresTouchscreen) {
                org.w3c.dom.Document document = context.getDocument();
                org.w3c.dom.Element element = null;
                if (document != null) {
                    element = document.getDocumentElement();
                }
                if (element == null) {
                    return;
                }
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Hardware feature `android.hardware.touchscreen` must be declared as not required for Android TV");
            } else if (mTouchscreenRequired) {
                org.w3c.dom.Element element = mTouchscreenElement;
                if (element == null) {
                    org.w3c.dom.Document document = context.getDocument();
                    if (document != null) {
                        element = document.getDocumentElement();
                    }
                }
                if (element == null) {
                    return;
                }
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Hardware feature `android.hardware.touchscreen` must be declared with `android:required=\"false\"` for Android TV");
            }
        }
    }
}