package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@SuppressWarnings("UnstableApiUsage")
public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_NAME = "name";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_ACTIVITY_ALIAS = "activity-alias";
    private static final String TAG_CATEGORY = "category";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_MANIFEST = "manifest";
    private static final String TAG_USES_FEATURE = "uses-feature";

    private static final String FEATURE_LEANBACK = "android.software.leanback";
    private static final String CATEGORY_LEANBACK_LAUNCHER =
            "android.intent.category.LEANBACK_LAUNCHER";

    public static final Issue ISSUE = Issue.create(
            "MissingLeanbackSupport",
            "Missing Leanback Support",
            "The manifest should declare the use of the Leanback user interface required by "
                    + "Android TV. To fix this, add\n"
                    + "`<uses-feature android:name=\"android.software.leanback\"\n"
                    + "                android:required=\"false\" />`\n"
                    + "to your manifest.\n"
                    + "Reference documentation:\n"
                    + "  - https://developer.android.com/training/tv/start/start.html#leanback-req",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_MANIFEST);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (hasLeanbackFeature(element)) {
            return;
        }

        if (isAndroidTvApp(element)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing Leanback Support"
            );
        }
    }

    private static boolean hasLeanbackFeature(Element manifest) {
        NodeList features = manifest.getElementsByTagName(TAG_USES_FEATURE);
        for (int i = 0; i < features.getLength(); i++) {
            Node node = features.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element feature = (Element) node;
                String name = feature.getAttributeNS(ANDROID_NS, ATTR_NAME);
                if (FEATURE_LEANBACK.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isAndroidTvApp(Element manifest) {
        if (hasLeanbackLauncher(manifest.getElementsByTagName(TAG_ACTIVITY))) {
            return true;
        }
        return hasLeanbackLauncher(manifest.getElementsByTagName(TAG_ACTIVITY_ALIAS));
    }

    private static boolean hasLeanbackLauncher(NodeList components) {
        for (int i = 0; i < components.getLength(); i++) {
            Node componentNode = components.item(i);
            if (componentNode.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element component = (Element) componentNode;

            NodeList filters = component.getElementsByTagName(TAG_INTENT_FILTER);
            for (int j = 0; j < filters.getLength(); j++) {
                Node filterNode = filters.item(j);
                if (filterNode.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element filter = (Element) filterNode;

                NodeList categories = filter.getElementsByTagName(TAG_CATEGORY);
                for (int k = 0; k < categories.getLength(); k++) {
                    Node categoryNode = categories.item(k);
                    if (categoryNode.getNodeType() != Node.ELEMENT_NODE) {
                        continue;
                    }
                    Element category = (Element) categoryNode;
                    String name = category.getAttributeNS(ANDROID_NS, ATTR_NAME);
                    if (CATEGORY_LEANBACK_LAUNCHER.equals(name)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}