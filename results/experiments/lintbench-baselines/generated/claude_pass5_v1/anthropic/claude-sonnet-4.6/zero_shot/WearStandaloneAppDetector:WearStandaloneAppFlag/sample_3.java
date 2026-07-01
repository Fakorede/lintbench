package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.Detector;

import org.w3c.dom.Attr;
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

/**
 * Lint detector that checks for the presence and validity of the Wear standalone app flag.
 */
public class WearStandaloneAppDetector extends Detector implements XmlScanner {

    private static final String WEARABLE_STANDALONE_META_DATA =
            "com.google.android.wearable.standalone";

    /** The main issue detected by this detector */
    public static final Issue ISSUE = Issue.create(
            "WearStandaloneAppFlag",
            "Invalid or missing Wear standalone app flag",
            "Wearable apps should specify whether they can work standalone, without a phone app. "
                    + "Add a valid meta-data entry for `com.google.android.wearable.standalone` to "
                    + "your application element and set the value to `true` or `false`.\n"
                    + "```xml\n"
                    + "<meta-data android:name=\"com.google.android.wearable.standalone\"\n"
                    + "           android:value=\"true\"/>\n"
                    + "```\n",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    WearStandaloneAppDetector.class,
                    Scope.MANIFEST_SCOPE))
            .addMoreInfo("https://developer.android.com/training/wearables/apps/packaging.html");

    /** Constructs a new {@link WearStandaloneAppDetector} */
    public WearStandaloneAppDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_APPLICATION);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // We only care about the <application> element in the manifest
        if (!TAG_APPLICATION.equals(element.getTagName())) {
            return;
        }

        // Look through child elements for a meta-data element with the standalone key
        NodeList children = element.getChildNodes();
        boolean foundStandaloneMetaData = false;

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element childElement = (Element) child;
            if (!TAG_META_DATA.equals(childElement.getTagName())) {
                continue;
            }

            // Check if this meta-data has the standalone name
            String nameAttr = childElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (!WEARABLE_STANDALONE_META_DATA.equals(nameAttr)) {
                continue;
            }

            // Found the standalone meta-data element; now validate the value
            foundStandaloneMetaData = true;

            String valueAttr = childElement.getAttributeNS(ANDROID_URI, ATTR_VALUE);
            if (valueAttr == null || valueAttr.isEmpty()) {
                // Missing value attribute
                context.report(
                        ISSUE,
                        childElement,
                        context.getLocation(childElement),
                        "The `" + WEARABLE_STANDALONE_META_DATA + "` meta-data must have "
                                + "an `android:value` attribute set to `true` or `false`");
            } else if (!valueAttr.equals("true") && !valueAttr.equals("false")) {
                // Invalid value attribute
                Attr valueNode = childElement.getAttributeNodeNS(ANDROID_URI, ATTR_VALUE);
                context.report(
                        ISSUE,
                        childElement,
                        valueNode != null
                                ? context.getLocation(valueNode)
                                : context.getLocation(childElement),
                        "The `" + WEARABLE_STANDALONE_META_DATA + "` meta-data value must be "
                                + "`true` or `false`, but was `" + valueAttr + "`");
            }

            // We found the element (whether valid or not), no need to continue searching
            break;
        }

        if (!foundStandaloneMetaData) {
            // The standalone meta-data element is missing entirely
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Wearable apps should specify whether they can work standalone, without a "
                            + "phone app. Add a `<meta-data "
                            + "android:name=\"" + WEARABLE_STANDALONE_META_DATA + "\" "
                            + "android:value=\"true\"/>` entry to your `<application>` element");
        }
    }
}