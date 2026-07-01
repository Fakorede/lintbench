package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class WearStandaloneAppDetector extends ResourceXmlDetector {
    public static final Issue ISSUE = Issue.create(
        "WearStandaloneAppFlag",
        "Invalid or missing Wear standalone app flag",
        "Wearable apps should specify whether they can work standalone, without a phone app. " +
        "Add a valid meta-data entry for `com.google.android.wearable.standalone` to " +
        "your application element and set the value to `true` or `false`.\n" +
        "```xml\n" +
        "<meta-data android:name=\"com.google.android.wearable.standalone\"\n" +
        "           android:value=\"true\"/>\n" +
        "```\n",
        Category.CORRECTNESS,
        6,
        Severity.WARNING,
        new Implementation(WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE)
    );

    private static final String META_DATA_STANDALONE = "com.google.android.wearable.standalone";

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_APPLICATION);
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        boolean found = false;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (SdkConstants.TAG_META_DATA.equals(childElement.getTagName())) {
                    String name = childElement.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                    if (META_DATA_STANDALONE.equals(name)) {
                        found = true;
                        Attr valueAttr = childElement.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_VALUE);
                        if (valueAttr != null) {
                            String value = valueAttr.getValue();
                            if (!"true".equals(value) && !"false".equals(value)) {
                                context.report(ISSUE, childElement, context.getLocation(valueAttr),
                                    "Invalid value for Wear standalone app flag; must be `true` or `false`");
                            }
                        } else {
                            context.report(ISSUE, childElement, context.getLocation(childElement),
                                "Missing `android:value` for Wear standalone app flag; must be `true` or `false`");
                        }
                        break;
                    }
                }
            }
        }

        if (!found) {
            context.report(ISSUE, element, context.getLocation(element),
                "Missing Wear standalone app flag; add `<meta-data android:name=\"com.google.android.wearable.standalone\" android:value=\"true|false\"/>`");
        }
    }
}