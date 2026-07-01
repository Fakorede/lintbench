package com.android.tools.lint.checks;

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

public class WearStandaloneAppDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "WearStandaloneAppFlag",
            "Invalid or missing Wear standalone app flag",
            "Wearable apps should specify whether they can work standalone, without a phone app. " +
            "Add a valid meta-data entry for `com.google.android.wearable.standalone` to " +
            "your application element and set the value to `true` or `false`.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    WearStandaloneAppDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    );

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        if (!hasWatchFeature(root)) {
            return;
        }

        NodeList applications = root.getElementsByTagName("application");
        if (applications.getLength() == 0) {
            return;
        }
        Element application = (Element) applications.item(0);

        Element standaloneMeta = null;
        Node child = application.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE && "meta-data".equals(child.getNodeName())) {
                Element meta = (Element) child;
                String name = meta.getAttributeNS(SdkConstants.ANDROID_URI, "name");
                if ("com.google.android.wearable.standalone".equals(name)) {
                    standaloneMeta = meta;
                    break;
                }
            }
            child = child.getNextSibling();
        }

        if (standaloneMeta == null) {
            context.report(
                    ISSUE,
                    application,
                    context.getNameLocation(application),
                    "Missing `com.google.android.wearable.standalone` meta-data flag"
            );
        } else {
            String value = standaloneMeta.getAttributeNS(SdkConstants.ANDROID_URI, "value");
            if (!"true".equals(value) && !"false".equals(value)) {
                context.report(
                        ISSUE,
                        standaloneMeta,
                        context.getLocation(standaloneMeta),
                        "The `com.google.android.wearable.standalone` value must be either `true` or `false`"
                );
            }
        }
    }

    private boolean hasWatchFeature(Element root) {
        NodeList features = root.getElementsByTagName("uses-feature");
        for (int i = 0; i < features.getLength(); i++) {
            Node node = features.item(i);
            if (node instanceof Element) {
                Element feature = (Element) node;
                String name = feature.getAttributeNS(SdkConstants.ANDROID_URI, "name");
                if ("android.hardware.type.watch".equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }
}