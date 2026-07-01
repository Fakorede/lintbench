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
                    "Wearable apps should specify whether they can work standalone, without a phone app. "
                    + "Add a valid meta-data entry for `com.google.android.wearable.standalone` to "
                    + "your application element and set the value to `true` or `false`.\n"
                    + "```xml\n"
                    + "<meta-data android:name=\"com.google.android.wearable.standalone\"\n"
                    + "           android:value=\"true\"/>\n"
                    + "```",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private static final String META_DATA_STANDALONE = "com.google.android.wearable.standalone";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private boolean mFoundValidFlag;
    private Element mApplicationElement;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mFoundValidFlag = false;
        mApplicationElement = null;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("application");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        mApplicationElement = element;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if ("meta-data".equals(childElement.getTagName())) {
                    String name = childElement.getAttributeNS(ANDROID_URI, "name");
                    if (META_DATA_STANDALONE.equals(name)) {
                        String value = childElement.getAttributeNS(ANDROID_URI, "value");
                        if ("true".equals(value) || "false".equals(value)) {
                            mFoundValidFlag = true;
                        } else {
                            context.report(
                                    ISSUE,
                                    context.getLocation(childElement),
                                    "Invalid value for wearable standalone flag; must be \"true\" or \"false\"");
                            mFoundValidFlag = true;
                        }
                    }
                }
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mApplicationElement != null && !mFoundValidFlag) {
            context.report(
                    ISSUE,
                    context.getLocation(mApplicationElement),
                    "Missing wearable standalone app flag; add <meta-data android:name=\"com.google.android.wearable.standalone\" android:value=\"true|false\"/>");
        }
    }
}