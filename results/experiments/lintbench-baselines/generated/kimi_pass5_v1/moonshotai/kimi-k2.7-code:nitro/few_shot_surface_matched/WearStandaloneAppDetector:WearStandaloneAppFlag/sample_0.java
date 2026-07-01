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
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class WearStandaloneAppDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "WearStandaloneAppFlag",
                    "Invalid or missing Wear standalone app flag",
                    "Wearable apps should specify whether they can work standalone, without a phone "
                            + "app. Add a valid `<meta-data>` entry for "
                            + "`com.google.android.wearable.standalone` to the `<application>` "
                            + "element and set the value to `true` or `false`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE));

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String STANDALONE_FLAG = "com.google.android.wearable.standalone";
    private static final String WEAR_FEATURE = "android.hardware.type.watch";
    private static final String TAG_APPLICATION = "application";
    private static final String TAG_METADATA = "meta-data";
    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_VALUE = "value";

    private boolean mHasWatchFeature;
    private Element mApplicationElement;
    private Element mStandaloneMetaData;
    private boolean mStandaloneValueValid;

    @Override
    public void beforeCheckFile(Context context) {
        mHasWatchFeature = false;
        mApplicationElement = null;
        mStandaloneMetaData = null;
        mStandaloneValueValid = false;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_APPLICATION, TAG_METADATA, TAG_USES_FEATURE);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        if (TAG_USES_FEATURE.equals(tag)) {
            String name = getAttribute(element, ATTR_NAME);
            if (WEAR_FEATURE.equals(name)) {
                mHasWatchFeature = true;
            }
        } else if (TAG_APPLICATION.equals(tag)) {
            mApplicationElement = element;
        } else if (TAG_METADATA.equals(tag)) {
            String name = getAttribute(element, ATTR_NAME);
            if (STANDALONE_FLAG.equals(name) && isApplicationChild(element)) {
                mStandaloneMetaData = element;
                String value = getAttribute(element, ATTR_VALUE);
                if ("true".equals(value) || "false".equals(value)) {
                    mStandaloneValueValid = true;
                }
            }
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        if (!mHasWatchFeature || !(context instanceof XmlContext)) {
            return;
        }

        XmlContext xmlContext = (XmlContext) context;
        if (mStandaloneMetaData != null) {
            if (!mStandaloneValueValid) {
                xmlContext.report(
                        ISSUE,
                        mStandaloneMetaData,
                        xmlContext.getLocation(mStandaloneMetaData),
                        "Invalid Wear standalone app flag: the value must be `true` or `false`");
            }
        } else if (mApplicationElement != null) {
            xmlContext.report(
                    ISSUE,
                    mApplicationElement,
                    xmlContext.getLocation(mApplicationElement),
                    "Missing Wear standalone app flag. Add `<meta-data android:name=\""
                            + STANDALONE_FLAG
                            + "\" android:value=\"true|false\" />` to the `<application>` element");
        }
    }

    private static boolean isApplicationChild(Element element) {
        Node parent = element.getParentNode();
        return parent instanceof Element
                && TAG_APPLICATION.equals(((Element) parent).getTagName());
    }

    private static String getAttribute(Element element, String localName) {
        String value = element.getAttributeNS(ANDROID_NS, localName);
        if (value == null || value.isEmpty()) {
            value = element.getAttribute(localName);
        }
        return value;
    }
}