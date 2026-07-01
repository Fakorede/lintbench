package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Detector.XmlScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class WearStandaloneAppDetector extends Detector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String WEARABLE_FEATURE = "android.hardware.type.watch";
    private static final String STANDALONE_METADATA = "com.google.android.wearable.standalone";

    private static final Implementation IMPLEMENTATION =
            new Implementation(WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "WearStandaloneAppFlag",
                    "Invalid or missing Wear standalone app flag",
                    "Wearable apps must declare whether they can operate independently of a "
                            + "companion phone app. Add a <meta-data> element inside the "
                            + "<application> element with "
                            + "android:name=\"com.google.android.wearable.standalone\" and "
                            + "android:value=\"true\" or \"false\".",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mIsWearApp;
    private boolean mHasValidStandaloneFlag;
    private Element mApplicationElement;

    @Override
    public void beforeCheckFile(Context context) {
        mIsWearApp = false;
        mHasValidStandaloneFlag = false;
        mApplicationElement = null;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("application", "uses-feature");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();

        if ("uses-feature".equals(tag)) {
            String name = getAttribute(element, "name");
            if (WEARABLE_FEATURE.equals(name)) {
                mIsWearApp = true;
            }
        } else if ("application".equals(tag)) {
            mApplicationElement = element;

            NodeList children = element.getChildNodes();
            for (int i = 0, n = children.getLength(); i < n; i++) {
                Node child = children.item(i);
                if (child.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element childElement = (Element) child;
                if (!"meta-data".equals(childElement.getTagName())) {
                    continue;
                }

                String name = getAttribute(childElement, "name");
                if (STANDALONE_METADATA.equals(name)) {
                    String value = getAttribute(childElement, "value");
                    if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                        mHasValidStandaloneFlag = true;
                    } else {
                        context.report(
                                ISSUE,
                                childElement,
                                context.getLocation(childElement),
                                "Wear standalone app flag must be set to true or false.");
                    }
                }
            }
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        if (!mIsWearApp || mApplicationElement == null || mHasValidStandaloneFlag) {
            return;
        }

        context.report(
                ISSUE,
                mApplicationElement,
                context.getLocation(mApplicationElement),
                "Missing Wear standalone app flag. Add a <meta-data> element to the "
                        + "<application> element with "
                        + "android:name=\"com.google.android.wearable.standalone\" and "
                        + "android:value=\"true\" or \"false\".");
    }

    private static String getAttribute(Element element, String localName) {
        String value = element.getAttributeNS(ANDROID_URI, localName);
        if (value.isEmpty()) {
            value = element.getAttribute(localName);
        }
        return value;
    }
}