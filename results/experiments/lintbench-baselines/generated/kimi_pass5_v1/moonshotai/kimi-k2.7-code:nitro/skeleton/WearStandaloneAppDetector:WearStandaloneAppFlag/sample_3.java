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

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String META_DATA_TAG = "meta-data";
    private static final String STANDALONE_FLAG = "com.google.android.wearable.standalone";
    private static final String VALUE_TRUE = "true";
    private static final String VALUE_FALSE = "false";

    private static final Implementation IMPLEMENTATION =
            new Implementation(WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "WearStandaloneAppFlag",
                    "Invalid or missing Wear standalone app flag",
                    "Wearable apps must declare the <code>com.google.android.wearable.standalone</code> "
                            + "meta-data flag in the <code>&lt;application&gt;</code> element, with a value "
                            + "of either <code>true</code> or <code>false</code>, to indicate whether the "
                            + "app can function without a companion phone app.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mFoundFlag;
    private boolean mValidFlag;
    private Element mApplicationElement;
    private Element mFlagElement;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mFoundFlag = false;
        mValidFlag = false;
        mApplicationElement = null;
        mFlagElement = null;
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
            Node childNode = children.item(i);
            if (childNode.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            if (!META_DATA_TAG.equals(childNode.getNodeName())) {
                continue;
            }

            Element child = (Element) childNode;
            String name = child.getAttributeNS(ANDROID_URI, "name");
            if (STANDALONE_FLAG.equals(name)) {
                mFoundFlag = true;
                mFlagElement = child;

                String value = child.getAttributeNS(ANDROID_URI, "value");
                mValidFlag = VALUE_TRUE.equals(value) || VALUE_FALSE.equals(value);
                break;
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        XmlContext xmlContext = (XmlContext) context;

        if (!mFoundFlag) {
            xmlContext.report(
                    ISSUE,
                    xmlContext.getLocation(mApplicationElement),
                    "Missing Wear standalone app flag. Add a <meta-data> element with "
                            + "android:name=\"com.google.android.wearable.standalone\" and "
                            + "android:value=\"true\" or \"false\" to the <application> element.");
        } else if (!mValidFlag) {
            xmlContext.report(
                    ISSUE,
                    xmlContext.getLocation(mFlagElement),
                    "Invalid Wear standalone app flag value. The value must be either \"true\" or \"false\".");
        }
    }
}