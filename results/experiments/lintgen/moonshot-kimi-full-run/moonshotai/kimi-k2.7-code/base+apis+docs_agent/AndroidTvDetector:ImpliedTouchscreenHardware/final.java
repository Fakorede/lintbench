package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ImpliedTouchscreenHardware",
            "TV app does not declare touchscreen as not required",
            "Apps require the `android.hardware.touchscreen` feature by default. "
                    + "If your app is intended to run on TV, you must explicitly declare "
                    + "that a touchscreen is not required using "
                    + "`<uses-feature android:name=\"android.hardware.touchscreen\" "
                    + "android:required=\"false\"/>`.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    private static final String HARDWARE_TOUCHSCREEN = "android.hardware.touchscreen";
    private static final String HARDWARE_TYPE_TELEVISION = "android.hardware.type.television";
    private static final String SOFTWARE_LEANBACK = "android.software.leanback";
    private static final String CATEGORY_LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER";

    private boolean mHasTvFeatureOrCategory;
    private boolean mTouchscreenNotRequired;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                SdkConstants.TAG_MANIFEST,
                SdkConstants.TAG_USES_FEATURE,
                SdkConstants.TAG_CATEGORY
        );
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        mHasTvFeatureOrCategory = false;
        mTouchscreenNotRequired = false;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        if (SdkConstants.TAG_USES_FEATURE.equals(tag)) {
            String name = getAndroidAttribute(element, SdkConstants.ATTR_NAME);
            if (HARDWARE_TOUCHSCREEN.equals(name)) {
                String required = getAndroidAttribute(element, SdkConstants.ATTR_REQUIRED);
                if (SdkConstants.VALUE_FALSE.equals(required)) {
                    mTouchscreenNotRequired = true;
                }
            } else if (HARDWARE_TYPE_TELEVISION.equals(name)
                    || SOFTWARE_LEANBACK.equals(name)) {
                mHasTvFeatureOrCategory = true;
            }
        } else if (SdkConstants.TAG_CATEGORY.equals(tag)) {
            String name = getAndroidAttribute(element, SdkConstants.ATTR_NAME);
            if (CATEGORY_LEANBACK_LAUNCHER.equals(name)) {
                mHasTvFeatureOrCategory = true;
            }
        }
    }

    @Override
    public void visitElementAfter(XmlContext context, Element element) {
        if (SdkConstants.TAG_MANIFEST.equals(element.getTagName())) {
            if (mHasTvFeatureOrCategory && !mTouchscreenNotRequired) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "For TV apps, you must explicitly declare "
                                + "`android.hardware.touchscreen` with `android:required=\"false\"`"
                );
            }
            mHasTvFeatureOrCategory = false;
            mTouchscreenNotRequired = false;
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return false;
    }

    private static String getAndroidAttribute(Element element, String localName) {
        return element.getAttributeNS(SdkConstants.ANDROID_URI, localName);
    }
}