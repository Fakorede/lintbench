package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.ResourceXmlDetector;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_META_DATA;

/**
 * Detector for missing or invalid Wear standalone app flag.
 */
public class WearStandaloneAppDetector extends ResourceXmlDetector implements XmlScanner {

    private static final String WEAR_STANDALONE_META_DATA_NAME =
            "com.google.android.wearable.standalone";

    private static final String ATTR_NAME = "name";
    private static final String ATTR_VALUE = "value";

    public static final Issue ISSUE = Issue.create(
            "WearStandaloneAppFlag",
            "Invalid or missing Wear standalone app flag",
            "Wearable apps should specify whether they can work standalone, without a phone app. " +
            "Add a valid meta-data entry for `com.google.android.wearable.standalone` to " +
            "your application element and set the value to `true` or `false`.\n" +
            "```xml\n" +
            "<meta-data android:name=\"com.google.android.wearable.standalone\"\n" +
            "           android:value=\"true\"/>\n" +
            "```\n",
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
        return Collections.singletonList(TAG_APPLICATION);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Look for meta-data children of the application element
        Element standaloneMetaData = findStandaloneMetaData(element);

        if (standaloneMetaData == null) {
            // No standalone meta-data found at all
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing `<meta-data android:name=\"" + WEAR_STANDALONE_META_DATA_NAME + "\">` " +
                    "element in `<application>`");
            return;
        }

        // Found the meta-data element; now validate its value
        String value = standaloneMetaData.getAttributeNS(ANDROID_URI, ATTR_VALUE);
        if (value == null || value.isEmpty()) {
            context.report(
                    ISSUE,
                    standaloneMetaData,
                    context.getLocation(standaloneMetaData),
                    "The `" + WEAR_STANDALONE_META_DATA_NAME + "` meta-data is missing an " +
                    "`android:value` attribute; set it to `true` or `false`");
        } else if (!value.equals("true") && !value.equals("false")) {
            context.report(
                    ISSUE,
                    standaloneMetaData,
                    context.getLocation(standaloneMetaData.getAttributeNodeNS(ANDROID_URI, ATTR_VALUE)),
                    "The `" + WEAR_STANDALONE_META_DATA_NAME + "` meta-data `android:value` must " +
                    "be `true` or `false`, but was `" + value + "`");
        }
    }

    /**
     * Searches the direct children of the given application element for a meta-data element
     * with name {@value #WEAR_STANDALONE_META_DATA_NAME}.
     *
     * @param applicationElement the {@code <application>} element
     * @return the matching meta-data element, or {@code null} if not found
     */
    @Nullable
    private static Element findStandaloneMetaData(@NonNull Element applicationElement) {
        NodeList children = applicationElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (!TAG_META_DATA.equals(childElement.getLocalName())) {
                continue;
            }
            String name = childElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (WEAR_STANDALONE_META_DATA_NAME.equals(name)) {
                return childElement;
            }
        }
        return null;
    }
}