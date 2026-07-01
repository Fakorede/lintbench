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
                            AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private boolean mUsesLeanback;
    private boolean mDeclaresTouchscreenNotRequired;
    private org.w3c.dom.Element mManifestElement;

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Arrays.asList("uses-feature", "category", "manifest");
    }

    @Override
    public void beforeCheckFile(XmlContext context) {
        mUsesLeanback = false;
        mDeclaresTouchscreenNotRequired = false;
        mManifestElement = null;
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        String tagName = element.getTagName();
        if ("manifest".equals(tagName)) {
            mManifestElement = element;
        } else if ("uses-feature".equals(tagName)) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if ("android.software.leanback".equals(name)) {
                mUsesLeanback = true;
            } else if ("android.hardware.touchscreen".equals(name)) {
                String required = element.getAttributeNS("http://schemas.android.com/apk/res/android", "required");
                if ("false".equals(required)) {
                    mDeclaresTouchscreenNotRequired = true;
                }
            }
        } else if ("category".equals(tagName)) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                mUsesLeanback = true;
            }
        }
    }

    @Override
    public void afterCheckFile(XmlContext context) {
        if (mUsesLeanback && !mDeclaresTouchscreenNotRequired) {
            org.w3c.dom.Element target = mManifestElement;
            if (target == null && context.document != null) {
                target = context.document.getDocumentElement();
            }
            if (target != null) {
                context.report(
                        ISSUE,
                        target,
                        context.getLocation(target),
                        "An activity is a TV activity, but the `<uses-feature "
                                + "android:name=\"android.hardware.touchscreen\" "
                                + "android:required=\"false\"/>` tag is missing");
            }
        }
    }
}