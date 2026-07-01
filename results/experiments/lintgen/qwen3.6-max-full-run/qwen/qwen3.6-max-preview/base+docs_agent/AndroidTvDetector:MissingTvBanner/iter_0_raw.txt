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
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("application", "activity", "activity-alias");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!hasLeanbackLauncher(element)) {
            return;
        }

        boolean hasBanner = hasBannerAttribute(element);
        String tag = element.getTagName();
        if (!hasBanner && ("activity".equals(tag) || "activity-alias".equals(tag))) {
            Element application = getApplicationElement(element);
            if (application != null) {
                hasBanner = hasBannerAttribute(application);
            }
        }

        if (!hasBanner) {
            context.report(ISSUE, element, context.getLocation(element),
                    "TV applications must provide a home screen banner for each localization if they include a Leanback launcher intent filter.");
        }
    }

    private boolean hasLeanbackLauncher(Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if ("intent-filter".equals(childElement.getTagName())) {
                    NodeList categories = childElement.getElementsByTagName("category");
                    for (int j = 0; j < categories.getLength(); j++) {
                        Element category = (Element) categories.item(j);
                        String name = category.getAttributeNS(SdkConstants.ANDROID_URI, "name");
                        if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean hasBannerAttribute(Element element) {
        return element.hasAttributeNS(SdkConstants.ANDROID_URI, "banner");
    }

    private Element getApplicationElement(Element element) {
        Element manifest = element.getOwnerDocument().getDocumentElement();
        NodeList apps = manifest.getElementsByTagName("application");
        if (apps.getLength() > 0) {
            return (Element) apps.item(0);
        }
        return null;
    }
}