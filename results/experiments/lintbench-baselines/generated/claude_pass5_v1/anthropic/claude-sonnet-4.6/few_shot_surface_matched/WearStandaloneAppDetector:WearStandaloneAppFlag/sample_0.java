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

import java.util.Arrays;
import java.util.Collection;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_VALUE;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_META_DATA;

public class WearStandaloneAppDetector extends Detector implements XmlScanner {

    private static final String WEAR_STANDALONE_META_DATA =
            "com.google.android.wearable.standalone";

    public static final Issue ISSUE =
            Issue.create(
                    "WearStandaloneAppFlag",
                    "Invalid or missing Wear standalone app flag",
                    "Wearable apps should specify whether they can work standalone, without a "
                            + "phone app. Add a valid meta-data entry for "
                            + "`com.google.android.wearable.standalone` to your application "
                            + "element and set the value to `true` or `false`.\n"
                            + "```xml\n"
                            + "<meta-data android:name=\"com.google.android.wearable.standalone\"\n"
                            + "           android:value=\"true\"/>\n"
                            + "```\n",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE));

    private boolean mFoundApplication;
    private boolean mFoundStandaloneFlag;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mFoundApplication = false;
        mFoundStandaloneFlag = false;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_APPLICATION, TAG_META_DATA);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        if (TAG_APPLICATION.equals(tagName)) {
            mFoundApplication = true;
            // Check children for standalone meta-data
            NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element childElement = (Element) child;
                    if (TAG_META_DATA.equals(childElement.getTagName())) {
                        String name = childElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
                        if (WEAR_STANDALONE_META_DATA.equals(name)) {
                            String value = childElement.getAttributeNS(ANDROID_URI, ATTR_VALUE);
                            if ("true".equals(value) || "false".equals(value)) {
                                mFoundStandaloneFlag = true;
                            } else {
                                // Found the meta-data but value is invalid
                                mFoundStandaloneFlag = true; // mark as found to avoid duplicate
                                context.report(
                                        ISSUE,
                                        childElement,
                                        context.getElementLocation(childElement),
                                        "The `" + WEAR_STANDALONE_META_DATA + "` meta-data value "
                                                + "must be set to `true` or `false`");
                            }
                        }
                    }
                }
            }
        } else if (TAG_META_DATA.equals(tagName)) {
            // Handle meta-data elements visited directly
            Node parentNode = element.getParentNode();
            if (parentNode != null && TAG_APPLICATION.equals(parentNode.getNodeName())) {
                String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if (WEAR_STANDALONE_META_DATA.equals(name)) {
                    // Already handled in the application element visit above
                }
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mFoundApplication && !mFoundStandaloneFlag) {
            // We need to report on the application element
            // Since we don't have the element reference here, we report on the file
            context.report(
                    ISSUE,
                    context.getLocation(context.file),
                    "Wearable apps should specify whether they can work standalone, without "
                            + "a phone app, by setting the `"
                            + WEAR_STANDALONE_META_DATA
                            + "` meta-data entry to `true` or `false` in the application element");
        }
    }
}