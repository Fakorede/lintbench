package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_VALUE;
import static com.android.xml.AndroidManifest.NODE_APPLICATION;
import static com.android.xml.AndroidManifest.NODE_METADATA;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class WearStandaloneAppDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "WearStandaloneAppFlag",
                    "Invalid or missing Wear standalone app flag",
                    "Wearable apps should specify whether they can work standalone, without a"
                            + " phone app. Add a valid meta-data entry for"
                            + " `com.google.android.wearable.standalone` to your application"
                            + " element and set the value to `true` or `false`.\n"
                            + "```xml\n"
                            + "<meta-data android:name=\"com.google.android.wearable.standalone\"\n"
                            + "           android:value=\"true\"/>\n"
                            + "```",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE));

    private static final String WEARABLE_STANDALONE_KEY =
            "com.google.android.wearable.standalone";

    private boolean mFoundStandaloneFlag;
    private Element mApplicationElement;

    @Override
    public void beforeCheckFile(@NonNull com.android.tools.lint.detector.api.Context context) {
        mFoundStandaloneFlag = false;
        mApplicationElement = null;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(NODE_APPLICATION, NODE_METADATA);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        if (NODE_APPLICATION.equals(tagName)) {
            mApplicationElement = element;
        } else if (NODE_METADATA.equals(tagName)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (WEARABLE_STANDALONE_KEY.equals(name)) {
                // Check that the parent is the application element
                Node parent = element.getParentNode();
                if (parent == null || !NODE_APPLICATION.equals(parent.getNodeName())) {
                    return;
                }

                String value = element.getAttributeNS(ANDROID_URI, ATTR_VALUE);
                if ("true".equals(value) || "false".equals(value)) {
                    mFoundStandaloneFlag = true;
                } else {
                    mFoundStandaloneFlag = true; // found but invalid
                    context.report(
                            ISSUE,
                            element,
                            context.getLocation(element),
                            "The `com.google.android.wearable.standalone` meta-data value must"
                                    + " be set to `true` or `false`");
                }
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull com.android.tools.lint.detector.api.Context context) {
        if (mApplicationElement != null && !mFoundStandaloneFlag) {
            XmlContext xmlContext = (XmlContext) context;
            xmlContext.report(
                    ISSUE,
                    mApplicationElement,
                    xmlContext.getLocation(mApplicationElement),
                    "Wearable apps should specify whether they can work standalone, without a"
                            + " phone app. Add a valid meta-data entry for"
                            + " `com.google.android.wearable.standalone` to your application"
                            + " element and set the value to `true` or `false`");
        }
    }
}