package com.android.tools.lint.checks;

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

import java.util.Arrays;
import java.util.Collection;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_CATEGORY;
import static com.android.SdkConstants.TAG_INTENT_FILTER;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

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
        return Arrays.asList(TAG_ACTIVITY, TAG_APPLICATION);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (hasLeanbackLauncherIntentFilter(element)) {
            if (!hasBannerAttribute(element)) {
                if (TAG_ACTIVITY.equals(element.getTagName())) {
                    Element application = getApplicationElement(element);
                    if (application != null && hasBannerAttribute(application)) {
                        return;
                    }
                }
                context.report(ISSUE, element, context.getLocation(element),
                        "TV apps must provide a home screen banner (`android:banner`) when using a Leanback launcher intent filter.");
            }
        }
    }

    private static boolean hasLeanbackLauncherIntentFilter(Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && TAG_INTENT_FILTER.equals(child.getNodeName())) {
                if (hasCategory((Element) child, LEANBACK_LAUNCHER_CATEGORY)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasCategory(Element intentFilter, String categoryName) {
        NodeList categories = intentFilter.getElementsByTagName(TAG_CATEGORY);
        for (int i = 0; i < categories.getLength(); i++) {
            Element category = (Element) categories.item(i);
            String name = category.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (categoryName.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasBannerAttribute(Element element) {
        return element.hasAttributeNS(ANDROID_URI, ATTR_BANNER);
    }

    private static Element getApplicationElement(Element activity) {
        Node parent = activity.getParentNode();
        while (parent != null) {
            if (parent.getNodeType() == Node.ELEMENT_NODE && TAG_APPLICATION.equals(parent.getNodeName())) {
                return (Element) parent;
            }
            parent = parent.getParentNode();
        }
        return null;
    }
}