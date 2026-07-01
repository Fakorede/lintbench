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
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;

public class WearStandaloneAppDetector extends Detector implements Detector.XmlScanner {

    private static final String STANDALONE_FLAG = "com.google.android.wearable.standalone";
    private static final String WATCH_FEATURE = "android.hardware.type.watch";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final String TAG_APPLICATION = "application";
    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String TAG_META_DATA = "meta-data";

    private static final String ATTR_NAME = "name";
    private static final String ATTR_VALUE = "value";
    private static final String ATTR_REQUIRED = "required";

    private static final Implementation IMPLEMENTATION =
            new Implementation(WearStandaloneAppDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "WearStandaloneAppFlag",
                    "Invalid or missing Wear standalone app flag",
                    "All Wear apps must specify whether the app can be used standalone, without a "
                            + "connected phone app. This is done by adding a `<meta-data>` element "
                            + "inside the `<application>` element with "
                            + "`android:name=\"com.google.android.wearable.standalone\"` and "
                            + "`android:value` set to either `\"true\"` or `\"false\"`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mIsWearableApp;
    private boolean mHasValidStandaloneFlag;
    private Element mApplicationElement;
    private Element mStandaloneMetaDataElement;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIsWearableApp = false;
        mHasValidStandaloneFlag = false;
        mApplicationElement = null;
        mStandaloneMetaDataElement = null;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_APPLICATION, TAG_USES_FEATURE, TAG_META_DATA);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();

        if (TAG_APPLICATION.equals(tag)) {
            mApplicationElement = element;
        } else if (TAG_USES_FEATURE.equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (WATCH_FEATURE.equals(name)) {
                String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
                if (required.isEmpty() || Boolean.parseBoolean(required)) {
                    mIsWearableApp = true;
                }
            }
        } else if (TAG_META_DATA.equals(tag) && isDirectChildOfApplication(element)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (STANDALONE_FLAG.equals(name)) {
                mStandaloneMetaDataElement = element;
                String value = element.getAttributeNS(ANDROID_URI, ATTR_VALUE);
                if ("true".equals(value) || "false".equals(value)) {
                    mHasValidStandaloneFlag = true;
                }
            }
        }
    }

    private static boolean isDirectChildOfApplication(@NonNull Element element) {
        return element.getParentNode() instanceof Element
                && TAG_APPLICATION.equals(((Element) element.getParentNode()).getTagName());
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!mIsWearableApp || mApplicationElement == null) {
            return;
        }

        XmlContext xmlContext = (XmlContext) context;

        if (mStandaloneMetaDataElement == null) {
            xmlContext.report(
                    ISSUE,
                    mApplicationElement,
                    xmlContext.getLocation(mApplicationElement),
                    "Missing Wear standalone app flag. Add `<meta-data android:name=\""
                            + STANDALONE_FLAG
                            + "\" android:value=\"true|false\" />` inside `<application>`.");
        } else if (!mHasValidStandaloneFlag) {
            xmlContext.report(
                    ISSUE,
                    mStandaloneMetaDataElement,
                    xmlContext.getLocation(mStandaloneMetaDataElement),
                    "The Wear standalone app flag must have a value of either \"true\" or \"false\".");
        }
    }
}