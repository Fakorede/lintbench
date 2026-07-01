package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class AndroidTvDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
        "MissingTvBanner",
        "TV Missing Banner",
        "A TV application must provide a home screen banner for each localization if it includes a Leanback launcher intent filter. " +
        "The banner is the app launch point that appears on the home screen in the apps and games rows.",
        Category.USABILITY,
        5,
        Severity.WARNING,
        new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    private static final String LEANBACK_LAUNCHER_CATEGORY = "android.intent.category.LEANBACK_LAUNCHER";
    private static final String ATTR_BANNER = "banner";

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_APPLICATION);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (element.hasAttributeNS(SdkConstants.ANDROID_URI, ATTR_BANNER)) {
            return;
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) node;
                String tag = child.getTagName();
                if (SdkConstants.TAG_ACTIVITY.equals(tag) || SdkConstants.TAG_ACTIVITY_ALIAS.equals(tag)) {
                    if (hasLeanbackLauncherIntent(child) && !child.hasAttributeNS(SdkConstants.ANDROID_URI, ATTR_BANNER)) {
                        context.report(ISSUE, child, context.getLocation(child),
                            "TV apps must provide a home screen banner for each localization if they include a Leanback launcher intent filter.");
                    }
                }
            }
        }
    }

    private static boolean hasLeanbackLauncherIntent(Element activity) {
        NodeList intentFilters = activity.getElementsByTagName(SdkConstants.TAG_INTENT_FILTER);
        for (int i = 0; i < intentFilters.getLength(); i++) {
            Element filter = (Element) intentFilters.item(i);
            NodeList categories = filter.getElementsByTagName(SdkConstants.TAG_CATEGORY);
            for (int j = 0; j < categories.getLength(); j++) {
                Element category = (Element) categories.item(j);
                String name = category.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                if (LEANBACK_LAUNCHER_CATEGORY.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }
}