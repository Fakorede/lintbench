package com.android.tools.lint.checks;

import com.android.SdkConstants;
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

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingLeanbackLauncher",
            "Missing Leanback Launcher Intent Filter",
            "An application intended to run on TV devices must declare a launcher " +
            "activity for TV in its manifest using a `android.intent.category.LEANBACK_LAUNCHER` " +
            "intent filter.",
            Category.COMPATIBILITY,
            5,
            Severity.ERROR,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_MANIFEST);
    }

    @Override
    public void visitElement(XmlContext context, Element manifest) {
        boolean hasLeanbackFeature = false;
        boolean hasLeanbackLauncher = false;
        Element leanbackFeatureElement = null;

        NodeList children = manifest.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            String tagName = childElement.getTagName();

            if (SdkConstants.TAG_USES_FEATURE.equals(tagName)) {
                String featureName = childElement.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                if ("android.software.leanback".equals(featureName)) {
                    hasLeanbackFeature = true;
                    leanbackFeatureElement = childElement;
                }
            } else if (SdkConstants.TAG_APPLICATION.equals(tagName)) {
                NodeList appChildren = childElement.getChildNodes();
                for (int j = 0; j < appChildren.getLength(); j++) {
                    Node appChild = appChildren.item(j);
                    if (appChild.getNodeType() != Node.ELEMENT_NODE) {
                        continue;
                    }
                    Element appChildElement = (Element) appChild;
                    String appChildTagName = appChildElement.getTagName();

                    if (SdkConstants.TAG_ACTIVITY.equals(appChildTagName) || "activity-alias".equals(appChildTagName)) {
                        if (hasLeanbackLauncherFilter(appChildElement)) {
                            hasLeanbackLauncher = true;
                        }
                    }
                }
            }
        }

        if (hasLeanbackFeature && !hasLeanbackLauncher) {
            context.report(
                    ISSUE,
                    leanbackFeatureElement != null ? leanbackFeatureElement : manifest,
                    context.getLocation(leanbackFeatureElement != null ? leanbackFeatureElement : manifest),
                    "Expected to find a leanback launcher activity"
            );
        }
    }

    private boolean hasLeanbackLauncherFilter(Element activityElement) {
        NodeList children = activityElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (SdkConstants.TAG_INTENT_FILTER.equals(childElement.getTagName())) {
                if (hasLeanbackCategory(childElement)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasLeanbackCategory(Element intentFilterElement) {
        NodeList children = intentFilterElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (SdkConstants.TAG_CATEGORY.equals(childElement.getTagName())) {
                String categoryName = childElement.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                if ("android.intent.category.LEANBACK_LAUNCHER".equals(categoryName)) {
                    return true;
                }
            }
        }
        return false;
    }
}