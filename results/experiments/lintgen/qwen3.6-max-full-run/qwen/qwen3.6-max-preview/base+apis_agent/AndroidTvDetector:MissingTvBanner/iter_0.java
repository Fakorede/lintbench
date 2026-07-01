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

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;

public class AndroidTvDetector extends Detector implements XmlScanner {

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

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(SdkConstants.TAG_ACTIVITY, SdkConstants.TAG_APPLICATION);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!hasLeanbackLauncherIntentFilter(element)) {
            return;
        }

        if (hasBannerAttribute(element)) {
            return;
        }

        // If the intent filter is on an activity, the banner can also be declared on the application tag
        if (SdkConstants.TAG_ACTIVITY.equals(element.getTagName())) {
            Element application = getApplicationElement(element);
            if (application != null && hasBannerAttribute(application)) {
                return;
            }
        }

        context.report(ISSUE, element, context.getLocation(element),
                "TV app missing `android:banner` attribute. " +
                "Apps with a Leanback launcher intent filter must provide a home screen banner.");
    }

    private boolean hasLeanbackLauncherIntentFilter(Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childEl = (Element) child;
                if (SdkConstants.TAG_INTENT_FILTER.equals(childEl.getTagName())) {
                    if (hasLeanbackCategory(childEl)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasLeanbackCategory(Element intentFilter) {
        NodeList categories = intentFilter.getElementsByTagName(SdkConstants.TAG_CATEGORY);
        for (int i = 0; i < categories.getLength(); i++) {
            Element cat = (Element) categories.item(i);
            String name = cat.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasBannerAttribute(Element element) {
        return element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_BANNER);
    }

    private Element getApplicationElement(Element activity) {
        Node parent = activity.getParentNode();
        while (parent != null) {
            if (parent.getNodeType() == Node.ELEMENT_NODE) {
                Element el = (Element) parent;
                if (SdkConstants.TAG_MANIFEST.equals(el.getTagName())) {
                    NodeList apps = el.getElementsByTagName(SdkConstants.TAG_APPLICATION);
                    if (apps.getLength() > 0) {
                        return (Element) apps.item(0);
                    }
                    return null;
                }
            }
            parent = parent.getParentNode();
        }
        return null;
    }
}