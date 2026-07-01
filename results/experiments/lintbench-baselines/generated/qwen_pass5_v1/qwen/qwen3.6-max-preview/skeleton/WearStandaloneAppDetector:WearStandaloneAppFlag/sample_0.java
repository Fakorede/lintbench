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
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class WearStandaloneAppDetector extends Detector implements XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(WearStandaloneAppDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "WearStandaloneAppFlag",
                    "Invalid or missing Wear standalone app flag",
                    "Wearable apps should specify whether they can work standalone, without a phone app. " +
                    "Add a valid meta-data entry for `com.google.android.wearable.standalone` to your application element " +
                    "and set the value to `true` or `false`.\n" +
                    "```xml\n" +
                    "<meta-data android:name=\"com.google.android.wearable.standalone\"\n" +
                    "           android:value=\"true\"/>\n" +
                    "```",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String META_DATA_NAME = "com.google.android.wearable.standalone";

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        // No per-file state required
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("application");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!"AndroidManifest.xml".equals(context.file.getName())) {
            return;
        }

        boolean foundValid = false;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childEl = (Element) child;
                if ("meta-data".equals(childEl.getTagName())) {
                    String name = childEl.getAttributeNS(ANDROID_URI, "name");
                    if (META_DATA_NAME.equals(name)) {
                        String value = childEl.getAttributeNS(ANDROID_URI, "value");
                        if ("true".equals(value) || "false".equals(value)) {
                            foundValid = true;
                        } else {
                            context.report(ISSUE, childEl, context.getLocation(childEl),
                                "Invalid value for Wear standalone app flag; must be \"true\" or \"false\"");
                            return;
                        }
                    }
                }
            }
        }

        if (!foundValid) {
            context.report(ISSUE, element, context.getLocation(element),
                "Missing Wear standalone app flag. Add <meta-data android:name=\"com.google.android.wearable.standalone\" android:value=\"true\"/> (or \"false\") to your <application> element.");
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        // No per-file state required
    }
}