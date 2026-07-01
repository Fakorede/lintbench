package com.android.tools.lint.checks;

import com.android.SdkConstants;
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

    public static final Issue ISSUE = Issue.create(
            "WearStandaloneAppFlag",
            "Invalid or missing Wear standalone app flag",
            "Wearable apps should specify whether they can work standalone, without a phone app. " +
            "Add a valid meta-data entry for `com.google.android.wearable.standalone` to " +
            "your application element and set the value to `true` or `false`.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("application");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        boolean found = false;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "meta-data".equals(child.getNodeName())) {
                Element meta = (Element) child;
                String name = meta.getAttributeNS(SdkConstants.ANDROID_URI, "name");
                if (META_DATA_STANDALONE.equals(name)) {
                    found = true;
                    String value = meta.getAttributeNS(SdkConstants.ANDROID_URI, "value");
                    if (!"true".equals(value) && !"false".equals(value)) {
                        context.report(ISSUE, context.getLocation(meta),
                                "Invalid value for Wear standalone app flag. Must be \"true\" or \"false\".");
                    }
                }
            }
        }

        if (!found) {
            context.report(ISSUE, context.getLocation(element),
                    "Missing Wear standalone app flag. Add <meta-data android:name=\"com.google.android.wearable.standalone\" android:value=\"true\"/> or \"false\".");
        }
    }
}