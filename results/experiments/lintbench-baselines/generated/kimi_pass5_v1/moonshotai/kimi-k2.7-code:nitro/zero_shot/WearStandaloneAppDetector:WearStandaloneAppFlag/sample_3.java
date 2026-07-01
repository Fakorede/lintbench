package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_REQUIRED;
import static com.android.SdkConstants.ATTR_VALUE;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_META_DATA;
import static com.android.SdkConstants.TAG_USES_FEATURE;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collections;
import java.util.EnumSet;

public class WearStandaloneAppDetector extends Detector implements Detector.ManifestScanner {

    private static final String STANDALONE_FLAG = "com.google.android.wearable.standalone";
    private static final String WEARABLE_FEATURE = "android.hardware.type.watch";

    public static final Issue ISSUE = Issue.create(
            "WearStandaloneAppFlag",
            "Invalid or missing Wear standalone app flag",
            "Wearable apps must declare whether they can run without a paired phone. "
                    + "Add a <meta-data> element to the <application> element with "
                    + "android:name=\"com.google.android.wearable.standalone\" and "
                    + "android:value=\"true\" or \"false\".",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE));

    @Override
    @NonNull
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_APPLICATION);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!TAG_APPLICATION.equals(element.getTagName()) || !isWearableApp(context)) {
            return;
        }

        boolean found = false;
        boolean valid = false;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE
                    || !TAG_META_DATA.equals(child.getNodeName())) {
                continue;
            }

            Element meta = (Element) child;
            String name = getManifestAttribute(meta, ATTR_NAME);
            if (!STANDALONE_FLAG.equals(name)) {
                continue;
            }

            found = true;
            String value = getManifestAttribute(meta, ATTR_VALUE);
            if ("true".equals(value) || "false".equals(value)) {
                valid = true;
            } else {
                context.report(
                        ISSUE,
                        context.getLocation(meta),
                        "The `com.google.android.wearable.standalone` meta-data value must be `true` or `false`.");
                return;
            }
        }

        if (!found) {
            context.report(
                    ISSUE,
                    context.getLocation(element),
                    "Missing `com.google.android.wearable.standalone` meta-data element. "
                            + "Wearable apps must declare whether they can run standalone.");
        }
    }

    private static boolean isWearableApp(@NonNull XmlContext context) {
        Element manifest = context.document.getDocumentElement();
        if (manifest == null) {
            return false;
        }

        NodeList features = manifest.getElementsByTagName(TAG_USES_FEATURE);
        for (int i = 0; i < features.getLength(); i++) {
            Element feature = (Element) features.item(i);
            String name = getManifestAttribute(feature, ATTR_NAME);
            if (WEARABLE_FEATURE.equals(name)) {
                String required = getManifestAttribute(feature, ATTR_REQUIRED);
                if (required == null || required.isEmpty() || !"false".equals(required)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String getManifestAttribute(@NonNull Element element, @NonNull String localName) {
        String value = element.getAttribute(ANDROID_URI, localName);
        if (value == null || value.isEmpty()) {
            value = element.getAttribute("android:" + localName);
        }
        return value;
    }
}