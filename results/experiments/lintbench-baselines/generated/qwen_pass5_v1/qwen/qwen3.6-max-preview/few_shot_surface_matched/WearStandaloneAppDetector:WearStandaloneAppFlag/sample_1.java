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

import java.util.Arrays;
import java.util.Collection;

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
        return Arrays.asList("application", "meta-data");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if ("application".equals(tag)) {
            applicationElement = element;
        } else if ("meta-data".equals(tag)) {
            Node parent = element.getParentNode();
            if (parent != null && "application".equals(parent.getNodeName())) {
                String name = element.getAttribute("android:name");
                if ("com.google.android.wearable.standalone".equals(name)) {
                    String value = element.getAttribute("android:value");
                    if ("true".equals(value) || "false".equals(value)) {
                        foundValidFlag = true;
                    } else {
                        context.report(ISSUE, element, context.getLocation(element),
                                "Invalid value for wearable standalone flag. Must be \"true\" or \"false\".");
                    }
                }
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!foundValidFlag && applicationElement != null) {
            context.report(ISSUE, context.getLocation(applicationElement),
                    "Wearable apps must specify whether they can work standalone. Add "
                            + "<meta-data android:name=\"com.google.android.wearable.standalone\" "
                            + "android:value=\"true|false\"/> to the application element.");
        }
    }
}