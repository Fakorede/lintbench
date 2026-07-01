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

    public static final Issue ISSUE =
            Issue.create(
                    "WearStandaloneAppFlag",
                    "Invalid or missing Wear standalone app flag",
                    "Wearable apps must specify whether they can work standalone, without a phone"
                            + " app. Add a valid `<meta-data>` entry with"
                            + " `android:name=\"com.google.android.wearable.standalone\"` to the"
                            + " `<application>` element and set `android:value` to either `true` or"
                            + " `false`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE));

    private static final String WEARABLE_STANDALONE_NAME =
            "com.google.android.wearable.standalone";
    private static final String WATCH_FEATURE = "android.hardware.type.watch";
    private static final String TAG_APPLICATION = "application";
    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String TAG_META_DATA = "meta-data";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_VALUE = "value";

    private boolean mHasWatchFeature;
    private boolean mHasStandaloneMetaData;
    private String mStandaloneValue;
    private org.w3c.dom.Element mApplicationElement;

    @Override
    public void beforeCheckFile(Context context) {
        mHasWatchFeature = false;
        mHasStandaloneMetaData = false;
        mStandaloneValue = null;
        mApplicationElement = null;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_APPLICATION, TAG_USES_FEATURE);
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        String tag = element.getTagName();
        if (TAG_USES_FEATURE.equals(tag)) {
            String name = getAttributeValue(element, ATTR_NAME);
            if (WATCH_FEATURE.equals(name)) {
                mHasWatchFeature = true;
            }
        } else if (TAG_APPLICATION.equals(tag)) {
            mApplicationElement = element;
            org.w3c.dom.NodeList children = element.getElementsByTagName(TAG_META_DATA);
            for (int i = 0; i < children.getLength(); i++) {
                org.w3c.dom.Node node = children.item(i);
                if (!(node instanceof org.w3c.dom.Element)) {
                    continue;
                }
                org.w3c.dom.Element meta = (org.w3c.dom.Element) node;
                String name = getAttributeValue(meta, ATTR_NAME);
                if (WEARABLE_STANDALONE_NAME.equals(name)) {
                    mHasStandaloneMetaData = true;
                    mStandaloneValue = getAttributeValue(meta, ATTR_VALUE);
                }
            }
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        XmlContext xmlContext = (XmlContext) context;

        if (mHasStandaloneMetaData) {
            if (mStandaloneValue == null
                    || (!mStandaloneValue.equals("true") && !mStandaloneValue.equals("false"))) {
                String message =
                        "The `com.google.android.wearable.standalone` meta-data value must be"
                                + " either `true` or `false`.";
                if (mApplicationElement != null) {
                    xmlContext.report(
                            ISSUE,
                            mApplicationElement,
                            xmlContext.getLocation(mApplicationElement),
                            message);
                } else {
                    xmlContext.report(
                            ISSUE,
                            xmlContext.file,
                            xmlContext.getLocation(xmlContext.file),
                            message);
                }
            }
        } else if (mHasWatchFeature) {
            String message =
                    "Wearable apps must specify the `com.google.android.wearable.standalone`"
                            + " meta-data flag in the `<application>` element with a value of"
                            + " `true` or `false`.";
            if (mApplicationElement != null) {
                xmlContext.report(
                        ISSUE,
                        mApplicationElement,
                        xmlContext.getLocation(mApplicationElement),
                        message);
            } else {
                xmlContext.report(
                        ISSUE,
                        xmlContext.file,
                        xmlContext.getLocation(xmlContext.file),
                        message);
            }
        }
    }

    private static String getAttributeValue(org.w3c.dom.Element element, String localName) {
        org.w3c.dom.NamedNodeMap attrs = element.getAttributes();
        if (attrs == null) {
            return null;
        }
        for (int i = 0; i < attrs.getLength(); i++) {
            org.w3c.dom.Node attr = attrs.item(i);
            String candidate = attr.getLocalName();
            if (candidate == null) {
                candidate = attr.getNodeName();
            }
            if (localName.equals(candidate)) {
                return attr.getNodeValue();
            }
        }
        return null;
    }
}