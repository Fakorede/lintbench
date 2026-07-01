package com.android.tools.lint.checks;

import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

public class WearStandaloneAppDetector extends Detector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TAG_APPLICATION = "application";
    private static final String TAG_META_DATA = "meta-data";
    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String TAG_USES_LIBRARY = "uses-library";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_VALUE = "value";
    private static final String ATTR_REQUIRED = "required";
    private static final String VALUE_TRUE = "true";
    private static final String VALUE_FALSE = "false";
    private static final String HARDWARE_TYPE_WATCH = "android.hardware.type.watch";
    private static final String WEARABLE_LIBRARY = "com.google.android.wearable";
    private static final String STANDALONE_FLAG = "com.google.android.wearable.standalone";

    public static final Issue ISSUE = Issue.create(
            "WearStandaloneAppFlag",
            "Invalid or missing Wear standalone app flag",
            "Wearable apps should specify whether they can work standalone, without a phone app. "
                    + "Add a valid meta-data entry for `com.google.android.wearable.standalone` to "
                    + "your application element and set the value to `true` or `false`.\n"
                    + "See https://developer.android.com/training/wearables/apps/packaging.html",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_APPLICATION);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!isWatchApp(element)) {
            return;
        }

        NodeList metaDataList = element.getElementsByTagName(TAG_META_DATA);
        boolean found = false;
        Element invalid = null;

        for (int i = 0; i < metaDataList.getLength(); i++) {
            Element metaData = (Element) metaDataList.item(i);
            String name = metaData.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (STANDALONE_FLAG.equals(name)) {
                found = true;
                String value = metaData.getAttributeNS(ANDROID_URI, ATTR_VALUE);
                if (!VALUE_TRUE.equals(value) && !VALUE_FALSE.equals(value)) {
                    invalid = metaData;
                }
            }
        }

        if (!found) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Missing Wear standalone app flag");
        } else if (invalid != null) {
            context.report(ISSUE, invalid, context.getLocation(invalid),
                    "Invalid Wear standalone app flag value");
        }
    }

    private boolean isWatchApp(Element application) {
        Node parent = application.getParentNode();
        if (parent == null || !(parent instanceof Element)) {
            return false;
        }
        Element manifest = (Element) parent;
        NodeList children = manifest.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) child;
            String tag = element.getTagName();
            if (TAG_USES_FEATURE.equals(tag)) {
                String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if (HARDWARE_TYPE_WATCH.equals(name) && isRequired(element)) {
                    return true;
                }
            } else if (TAG_USES_LIBRARY.equals(tag)) {
                String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if (WEARABLE_LIBRARY.equals(name) && isRequired(element)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isRequired(Element element) {
        String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
        return required.isEmpty() || VALUE_TRUE.equals(required);
    }
}