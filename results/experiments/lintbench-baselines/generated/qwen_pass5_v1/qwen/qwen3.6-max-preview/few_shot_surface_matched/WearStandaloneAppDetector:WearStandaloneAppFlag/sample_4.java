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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_VALUE;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_META_DATA;

public class WearStandaloneAppDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
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
            new Implementation(WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE));

    private static final String WEAR_STANDALONE_NAME = "com.google.android.wearable.standalone";

    private boolean mFoundStandaloneFlag;
    private Element mApplicationElement;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mFoundStandaloneFlag = false;
        mApplicationElement = null;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_APPLICATION);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        mApplicationElement = element;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (TAG_META_DATA.equals(childElement.getTagName())) {
                    String name = childElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
                    if (WEAR_STANDALONE_NAME.equals(name)) {
                        mFoundStandaloneFlag = true;
                        String value = childElement.getAttributeNS(ANDROID_URI, ATTR_VALUE);
                        if (!"true".equals(value) && !"false".equals(value)) {
                            context.report(ISSUE, childElement, context.getLocation(childElement),
                                    "The value for com.google.android.wearable.standalone must be \"true\" or \"false\"");
                        }
                        break;
                    }
                }
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!mFoundStandaloneFlag && mApplicationElement != null && context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            xmlContext.report(ISSUE, mApplicationElement, xmlContext.getLocation(mApplicationElement),
                    "Missing required meta-data tag: com.google.android.wearable.standalone");
        }
    }
}