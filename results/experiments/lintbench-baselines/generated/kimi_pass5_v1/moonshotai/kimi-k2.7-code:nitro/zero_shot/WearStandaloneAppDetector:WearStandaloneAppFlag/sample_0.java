package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_METADATA;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
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

public class WearStandaloneAppDetector extends Detector implements Detector.XmlScanner {

    private static final String STANDALONE_FLAG = "com.google.android.wearable.standalone";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_VALUE = "value";

    public static final Issue ISSUE =
            Issue.create(
                    "WearStandaloneAppFlag",
                    "Invalid or missing Wear standalone app flag",
                    "Wearable apps should specify whether they can work standalone, without a "
                            + "phone app. Add a valid meta-data entry for `"
                            + STANDALONE_FLAG
                            + "` to the application element and set the value to `true` or "
                            + "`false`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    new Implementation(WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE),
                    "https://developer.android.com/training/wearables/apps/packaging.html");

    @NonNull
    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_APPLICATION);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        boolean hasStandaloneFlag = false;
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && TAG_METADATA.equals(child.getNodeName())) {
                Element metadata = (Element) child;
                String name = metadata.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if (STANDALONE_FLAG.equals(name)) {
                    hasStandaloneFlag = true;
                    String value = metadata.getAttributeNS(ANDROID_URI, ATTR_VALUE);
                    if (!"true".equals(value) && !"false".equals(value)) {
                        context.report(
                                ISSUE,
                                context.getElementLocation(metadata),
                                "The `"
                                        + STANDALONE_FLAG
                                        + "` meta-data value must be `true` or `false`.");
                    }
                }
            }
        }

        if (!hasStandaloneFlag) {
            context.report(
                    ISSUE,
                    context.getElementLocation(element),
                    "Missing `"
                            + STANDALONE_FLAG
                            + "` meta-data entry on the application element.");
        }
    }
}