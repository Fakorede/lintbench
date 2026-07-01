package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class WearStandaloneAppDetector extends Detector implements Detector.XmlScanner {

    private static final String META_DATA_STANDALONE = "com.google.android.wearable.standalone";
    private static final String WEAR_FEATURE = "android.hardware.type.watch";

    private static final Implementation IMPLEMENTATION = new Implementation(
            WearStandaloneAppDetector.class,
            Scope.ANDROID_MANIFEST_SCOPE);

    public static final Issue ISSUE = Issue.create(
            "WearStandaloneAppFlag",
            "Invalid or missing Wear standalone app flag",
            "Wearable apps should specify whether they can work standalone, without a phone app. "
                    + "Add a valid meta-data entry for `com.google.android.wearable.standalone` "
                    + "to the application element and set the value to `true` or `false`.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("application");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!isWearApp(context)) {
            return;
        }

        boolean found = false;
        boolean valid = false;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && "meta-data".equals(child.getNodeName())) {
                Element meta = (Element) child;
                String name = meta.getAttribute("android:name");
                if (META_DATA_STANDALONE.equals(name)) {
                    found = true;
                    String value = meta.getAttribute("android:value");
                    if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                        valid = true;
                    } else {
                        context.report(ISSUE, meta, context.getLocation(meta),
                                "Invalid value for `com.google.android.wearable.standalone`; "
                                        + "must be `true` or `false`.");
                    }
                }
            }
        }

        if (!found) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Missing `com.google.android.wearable.standalone` meta-data flag.");
        }
    }

    private boolean isWearApp(XmlContext context) {
        NodeList features = context.document.getElementsByTagName("uses-feature");
        for (int i = 0; i < features.getLength(); i++) {
            Element feature = (Element) features.item(i);
            String name = feature.getAttribute("android:name");
            if (WEAR_FEATURE.equals(name)) {
                return true;
            }
        }
        return false;
    }
}