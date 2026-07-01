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
            "MissingTvBanner",
            "TV Missing Banner",
            "A TV application must provide a home screen banner for each localization " +
            "if it includes a Leanback launcher intent filter. The banner is the app " +
            "launch point that appears on the home screen in the apps and games rows.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(
                    AndroidTvDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    ).addMoreInfo("https://developer.android.com/training/tv/start/start.html#banner");

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_APPLICATION);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!SdkConstants.TAG_APPLICATION.equals(element.getTagName())) {
            return;
        }

        boolean hasLeanbackLauncher = false;

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                String tagName = childElement.getTagName();
                if (SdkConstants.TAG_ACTIVITY.equals(tagName) || SdkConstants.TAG_ACTIVITY_ALIAS.equals(tagName)) {
                    if (hasLeanbackLauncher(childElement)) {
                        hasLeanbackLauncher = true;
                        break;
                    }
                }
            }
        }

        if (hasLeanbackLauncher) {
            if (!element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_BANNER)) {
                context.report(
                        ISSUE,
                        element,
                        context.getNameLocation(element),
                        "Expect `android:banner` attribute with a Leanback launcher intent filter"
                );
            }
        }
    }

    private boolean hasLeanbackLauncher(Element activityElement) {
        NodeList children = activityElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (SdkConstants.TAG_INTENT_FILTER.equals(childElement.getTagName())) {
                    if (hasLeanbackCategory(childElement)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasLeanbackCategory(Element intentFilterElement) {
        NodeList children = intentFilterElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (SdkConstants.TAG_CATEGORY.equals(childElement.getTagName())) {
                    String categoryName = childElement.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                    if ("android.intent.category.LEANBACK_LAUNCHER".equals(categoryName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}