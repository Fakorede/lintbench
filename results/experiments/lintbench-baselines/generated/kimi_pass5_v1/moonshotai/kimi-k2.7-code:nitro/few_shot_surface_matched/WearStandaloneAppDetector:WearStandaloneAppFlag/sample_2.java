package com.android.tools.lint.checks;

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

public class WearStandaloneAppDetector extends Detector implements XmlScanner {

    private static final String WEARABLE_STANDALONE_METADATA_NAME =
            "com.google.android.wearable.standalone";
    private static final String FEATURE_WATCH = "android.hardware.type.watch";
    private static final String NODE_APPLICATION = "application";
    private static final String NODE_USES_FEATURE = "uses-feature";
    private static final String NODE_META_DATA = "meta-data";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_VALUE = "value";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    public static final Issue ISSUE =
            Issue.create(
                    "WearStandaloneAppFlag",
                    "Missing or invalid Wear standalone app flag",
                    "Wearable apps must specify whether they can work as a standalone app, without "
                            + "a phone app. Add a `<meta-data>` element inside the "
                            + "`<application>` element with android:name=\""
                            + WEARABLE_STANDALONE_METADATA_NAME
                            + "\" and set android:value to \"true\" or \"false\".",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE));

    private boolean mHasWatchFeature;
    private boolean mHasValidStandaloneFlag;
    private boolean mReportedInvalidValue;
    private org.w3c.dom.Element mApplicationElement;

    @Override
    public void beforeCheckFile(Context context) {
        mHasWatchFeature = false;
        mHasValidStandaloneFlag = false;
        mReportedInvalidValue = false;
        mApplicationElement = null;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(NODE_APPLICATION, NODE_USES_FEATURE);
    }

    @Override
    public void visitElement(
            @com.android.annotations.NonNull XmlContext context,
            @com.android.annotations.NonNull org.w3c.dom.Element element) {
        String tag = element.getTagName();
        if (NODE_USES_FEATURE.equals(tag)) {
            String name = element.getAttributeNS(ANDROID_NS, ATTR_NAME);
            if (FEATURE_WATCH.equals(name)) {
                mHasWatchFeature = true;
            }
        } else if (NODE_APPLICATION.equals(tag)) {
            mApplicationElement = element;
            org.w3c.dom.NodeList children = element.getElementsByTagName(NODE_META_DATA);
            int length = children.getLength();
            for (int i = 0; i < length; i++) {
                org.w3c.dom.Element meta = (org.w3c.dom.Element) children.item(i);
                String metaName = meta.getAttributeNS(ANDROID_NS, ATTR_NAME);
                if (WEARABLE_STANDALONE_METADATA_NAME.equals(metaName)) {
                    String value = meta.getAttributeNS(ANDROID_NS, ATTR_VALUE);
                    if ("true".equals(value) || "false".equals(value)) {
                        mHasValidStandaloneFlag = true;
                    } else {
                        context.report(
                                ISSUE,
                                meta,
                                context.getLocation(meta),
                                "The Wear standalone app flag must have a value of \"true\" or "
                                        + "\"false\".");
                        mReportedInvalidValue = true;
                    }
                }
            }
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        if (mHasWatchFeature
                && mApplicationElement != null
                && !mHasValidStandaloneFlag
                && !mReportedInvalidValue) {
            XmlContext xmlContext = (XmlContext) context;
            xmlContext.report(
                    ISSUE,
                    mApplicationElement,
                    xmlContext.getLocation(mApplicationElement),
                    "Wearable apps must specify the `com.google.android.wearable.standalone` "
                            + "meta-data flag in the `<application>` element with a value of "
                            + "\"true\" or \"false\".");
        }
    }
}