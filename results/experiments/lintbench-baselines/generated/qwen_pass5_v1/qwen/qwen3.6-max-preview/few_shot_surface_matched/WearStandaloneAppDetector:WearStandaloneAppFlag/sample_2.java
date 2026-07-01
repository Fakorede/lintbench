package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
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

import java.util.Collection;
import java.util.Collections;

public class WearStandaloneAppDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "WearStandaloneAppFlag",
                    "Invalid or missing Wear standalone app flag",
                    "Wearable apps should specify whether they can work standalone, without a phone app. "
                            + "Add a valid meta-data entry for `com.google.android.wearable.standalone` to "
                            + "your application element and set the value to `true` or `false`.\n"
                            + "```xml\n"
                            + "<meta-data android:name=\"com.google.android.wearable.standalone\"\n"
                            + "           android:value=\"true\"/>\n"
                            + "```",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(
                            WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE));

    private boolean foundValidFlag;
    private Element applicationElement;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        foundValidFlag = false;
        applicationElement = null;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("application");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        applicationElement = element;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element meta = (Element) child;
                if ("meta-data".equals(meta.getTagName())) {
                    String name = meta.getAttribute("android:name");
                    if ("com.google.android.wearable.standalone".equals(name)) {
                        String value = meta.getAttribute("android:value");
                        if ("true".equals(value) || "false".equals(value)) {
                            foundValidFlag = true;
                        } else {
                            context.report(ISSUE, meta, context.getLocation(meta),
                                    "Invalid value for wearable standalone flag. Must be \"true\" or \"false\".");
                        }
                    }
                }
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!foundValidFlag && applicationElement != null) {
            XmlContext xmlContext = (XmlContext) context;
            xmlContext.report(ISSUE, applicationElement, xmlContext.getLocation(applicationElement),
                    "Missing wearable standalone app flag. Add a valid meta-data entry for "
                            + "`com.google.android.wearable.standalone` to your application element "
                            + "and set the value to `true` or `false`.");
        }
    }
}