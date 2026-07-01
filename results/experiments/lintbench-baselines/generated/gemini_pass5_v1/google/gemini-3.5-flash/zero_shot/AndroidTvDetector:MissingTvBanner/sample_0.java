package com.android.tools.lint.checks;

import com.android.SdkConstants;
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
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingTvBanner",
            "TV Missing Banner",
            "A TV application must provide a home screen banner for each " +
            "localization if it includes a Leanback launcher intent filter. " +
            "The banner is the app launch point that appears on the home " +
            "screen in the apps and games rows.",
            Category.USABILITY,
            5,
            Severity.ERROR,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                SdkConstants.TAG_ACTIVITY,
                SdkConstants.TAG_ACTIVITY_ALIAS
        );
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (hasLeanbackLauncher(element)) {
            if (!hasBanner(element) && !hasApplicationBanner(element)) {
                context.report(
                        ISSUE,
                        element,
                        context.getNameLocation(element),
                        "Expect `android:banner` to be defined in the manifest for Leanback launcher activity"
                );
            }
        }
    }

    private boolean hasLeanbackLauncher(Element activity) {
        NodeList children = activity.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE &&
                    SdkConstants.TAG_INTENT_FILTER.equals(child.getNodeName())) {
                Element intentFilter = (Element) child;
                if (hasLeanbackCategory(intentFilter)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasLeanbackCategory(Element intentFilter) {
        NodeList children = intentFilter.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE &&
                    SdkConstants.TAG_CATEGORY.equals(child.getNodeName())) {
                Element category = (Element) child;
                String name = category.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasBanner(Element element) {
        return element.hasAttributeNS(SdkConstants.ANDROID_URI, "banner");
    }

    private boolean hasApplicationBanner(Element activity) {
        Node parent = activity.getParentNode();
        if (parent != null && parent.getNodeType() == Node.ELEMENT_NODE &&
                SdkConstants.TAG_APPLICATION.equals(parent.getNodeName())) {
            return hasBanner((Element) parent);
        }
        return false;
    }
}