package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class WearStandaloneAppDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "WearStandaloneAppFlag",
            "Invalid or missing Wear standalone app flag",
            "Wearable apps should specify whether they can work standalone, without a phone app. " +
            "Add a valid meta-data entry for `com.google.android.wearable.standalone` to " +
            "your application element and set the value to `true` or `false`.",
            Category.COMPLIANCE,
            6,
            Severity.WARNING,
            new Implementation(
                    WearStandaloneAppDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_MANIFEST);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Document document = element.getOwnerDocument();
        if (document == null) {
            return;
        }
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        boolean isWearApp = false;
        Element applicationElement = null;

        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) node;
                String tagName = child.getTagName();
                if (SdkConstants.TAG_USES_FEATURE.equals(tagName)) {
                    String name = child.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                    if ("android.hardware.type.watch".equals(name)) {
                        isWearApp = true;
                    }
                } else if (SdkConstants.TAG_APPLICATION.equals(tagName)) {
                    applicationElement = child;
                }
            }
        }

        if (!isWearApp) {
            return;
        }

        if (applicationElement == null) {
            context.report(
                    ISSUE,
                    root,
                    context.getLocation(root),
                    "Missing `<application>` tag required to configure Wear standalone mode"
            );
            return;
        }

        Element standaloneMetadata = null;
        NodeList appChildren = applicationElement.getChildNodes();
        for (int i = 0; i < appChildren.getLength(); i++) {
            Node node = appChildren.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) node;
                if (SdkConstants.TAG_META_DATA.equals(child.getTagName())) {
                    String name = child.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                    if ("com.google.android.wearable.standalone".equals(name)) {
                        standaloneMetadata = child;
                        break;
                    }
                }
            }
        }

        if (standaloneMetadata == null) {
            context.report(
                    ISSUE,
                    applicationElement,
                    context.getLocation(applicationElement),
                    "Expect `com.google.android.wearable.standalone` `<meta-data>` to be defined"
            );
        } else {
            String value = standaloneMetadata.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_VALUE);
            if (!"true".equals(value) && !"false".equals(value)) {
                context.report(
                        ISSUE,
                        standaloneMetadata,
                        context.getLocation(standaloneMetadata),
                        "The `android:value` attribute must be 'true' or 'false'"
                );
            }
        }
    }
}