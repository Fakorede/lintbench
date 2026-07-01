package com.android.tools.lint.checks;

import static org.w3c.dom.Node.ELEMENT_NODE;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class WearStandaloneAppDetector extends Detector implements XmlScanner.XmlScanner {

    public static final String ISSUE_ID = "WearStandaloneAppFlag";
    private static final String STANDALONE_METADATA = "com.google.android.wearable.standalone";

    private static final Implementation IMPLEMENTATION = new Implementation(
            WearStandaloneAppDetector.class,
            Scope.MANIFEST_SCOPE
    );

    public static final Issue ISSUE = Issue.create(
            ISSUE_ID,
            "Invalid or missing Wear standalone app flag",
            "Wearable apps should specify whether they can work standalone, without a phone app. "
                    + "Add a valid `<meta-data>` entry for `com.google.android.wearable.standalone` "
                    + "to the `<application>` element and set the value to `true` or `false`.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            IMPLEMENTATION
    );

    @Override
    public com.android.tools.lint.detector.api.Issue[] getApplicableElements() {
        return new com.android.tools.lint.detector.api.Issue[0];
    }

    @Override
    public String[] getApplicableElements() {
        return new String[]{SdkConstants.TAG_APPLICATION};
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!SdkConstants.TAG_APPLICATION.equals(element.getTagName())) {
            return;
        }

        if (!isWatchApp(context.document)) {
            return;
        }

        NodeList children = element.getChildNodes();
        boolean found = false;
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() != ELEMENT_NODE) {
                continue;
            }
            Element metaData = (Element) child;
            if (!SdkConstants.TAG_META_DATA.equals(metaData.getTagName())) {
                continue;
            }

            String name = getAttributeValue(metaData, SdkConstants.ATTR_NAME);
            if (!STANDALONE_METADATA.equals(name)) {
                continue;
            }

            found = true;
            String value = getAttributeValue(metaData, SdkConstants.ATTR_VALUE);
            if (!isValidBooleanValue(value)) {
                context.report(
                        ISSUE,
                        metaData,
                        context.getLocation(metaData),
                        "Invalid value for `com.google.android.wearable.standalone`: expected `true` or `false`."
                );
            }
        }

        if (!found) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing `com.google.android.wearable.standalone` meta-data element in `<application>`."
            );
        }
    }

    private static boolean isWatchApp(Document document) {
        NodeList features = document.getElementsByTagName(SdkConstants.TAG_USES_FEATURE);
        for (int i = 0, n = features.getLength(); i < n; i++) {
            Element feature = (Element) features.item(i);
            String name = getAttributeValue(feature, SdkConstants.ATTR_NAME);
            if (SdkConstants.FEATURE_WATCH.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isValidBooleanValue(String value) {
        return "true".equals(value) || "false".equals(value);
    }

    private static String getAttributeValue(Element element, String attribute) {
        String value = element.getAttributeNS(SdkConstants.ANDROID_URI, attribute);
        if (value.isEmpty()) {
            value = element.getAttribute(attribute);
        }
        return value;
    }
}