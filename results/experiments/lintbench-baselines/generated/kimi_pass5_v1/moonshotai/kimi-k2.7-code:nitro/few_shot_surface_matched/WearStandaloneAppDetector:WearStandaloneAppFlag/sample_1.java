package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;

public class WearStandaloneAppDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "WearStandaloneAppFlag",
                    "Invalid or missing Wear standalone app flag",
                    "Wearable apps should specify whether they can work standalone, without a phone"
                            + " app. Add a valid meta-data entry for"
                            + " `com.google.android.wearable.standalone` to the application element"
                            + " and set the value to `true` or `false`.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(
                            WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String WEARABLE_FEATURE = "android.hardware.type.watch";
    private static final String STANDALONE_META_DATA = "com.google.android.wearable.standalone";

    private boolean mIsWear;
    private boolean mHasStandaloneFlag;
    private Element mApplicationElement;

    @Override
    public void beforeCheckFile(XmlContext context) {
        mIsWear = false;
        mHasStandaloneFlag = false;
        mApplicationElement = null;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("uses-feature", "application");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        if ("uses-feature".equals(tag)) {
            String name = getAttributeValue(element, "name");
            if (WEARABLE_FEATURE.equals(name)) {
                mIsWear = true;
            }
        } else if ("application".equals(tag)) {
            mApplicationElement = element;
            NodeList children = element.getChildNodes();
            for (int i = 0, n = children.getLength(); i < n; i++) {
                Node child = children.item(i);
                if (child.getNodeType() != Node.ELEMENT_NODE
                        || !"meta-data".equals(child.getNodeName())) {
                    continue;
                }
                Element meta = (Element) child;
                String metaName = getAttributeValue(meta, "name");
                if (STANDALONE_META_DATA.equals(metaName)) {
                    String value = getAttributeValue(meta, "value");
                    if ("true".equals(value) || "false".equals(value)) {
                        mHasStandaloneFlag = true;
                    } else {
                        context.report(
                                ISSUE,
                                meta,
                                context.getLocation(meta),
                                "The `com.google.android.wearable.standalone` meta-data value"
                                        + " must be `true` or `false`.");
                        mHasStandaloneFlag = true;
                    }
                    break;
                }
            }
        }
    }

    @Override
    public void afterCheckFile(XmlContext context) {
        if (mIsWear && !mHasStandaloneFlag && mApplicationElement != null) {
            context.report(
                    ISSUE,
                    mApplicationElement,
                    context.getLocation(mApplicationElement),
                    "Wearable apps must specify a valid `com.google.android.wearable.standalone`"
                            + " meta-data flag in the application element.");
        }
    }

    private static String getAttributeValue(Element element, String localName) {
        String value = element.getAttributeNS(ANDROID_URI, localName);
        if (value.isEmpty()) {
            value = element.getAttribute(localName);
        }
        return value;
    }
}