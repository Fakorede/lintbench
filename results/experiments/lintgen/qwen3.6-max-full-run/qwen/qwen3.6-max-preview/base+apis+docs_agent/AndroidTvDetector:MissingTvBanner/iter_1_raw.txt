package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingTvBanner",
            "TV Missing Banner",
            "A TV application must provide a home screen banner for each localization if it includes a Leanback launcher intent filter. " +
            "The banner is the app launch point that appears on the home screen in the apps and games rows.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_MANIFEST);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Element application = getFirstChild(element, SdkConstants.TAG_APPLICATION);
        if (application == null) {
            return;
        }

        boolean appHasBanner = application.hasAttributeNS(SdkConstants.ANDROID_URI, "banner");

        NodeList children = application.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                String tag = child.getNodeName();
                if (SdkConstants.TAG_ACTIVITY.equals(tag) || SdkConstants.TAG_ACTIVITY_ALIAS.equals(tag)) {
                    Element activity = (Element) child;
                    if (hasLeanbackLauncher(activity)) {
                        if (!appHasBanner && !activity.hasAttributeNS(SdkConstants.ANDROID_URI, "banner")) {
                            context.report(ISSUE, context.getLocation(activity),
                                    "This TV activity does not have a banner defined. " +
                                    "Add `android:banner=\"@drawable/...\"` to the `<activity>` or `<application>` tag.");
                        }
                    }
                }
            }
        }
    }

    private static Element getFirstChild(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && tagName.equals(child.getNodeName())) {
                return (Element) child;
            }
        }
        return null;
    }

    private static boolean hasLeanbackLauncher(Element activity) {
        NodeList children = activity.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && SdkConstants.TAG_INTENT_FILTER.equals(child.getNodeName())) {
                Element filter = (Element) child;
                boolean hasMain = false;
                boolean hasLeanback = false;
                NodeList filterChildren = filter.getChildNodes();
                for (int j = 0; j < filterChildren.getLength(); j++) {
                    Node fc = filterChildren.item(j);
                    if (fc.getNodeType() == Node.ELEMENT_NODE) {
                        Element el = (Element) fc;
                        String tag = el.getTagName();
                        String name = el.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                        if (SdkConstants.TAG_ACTION.equals(tag) && "android.intent.action.MAIN".equals(name)) {
                            hasMain = true;
                        } else if (SdkConstants.TAG_CATEGORY.equals(tag) && "android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                            hasLeanback = true;
                        }
                    }
                }
                if (hasMain && hasLeanback) {
                    return true;
                }
            }
        }
        return false;
    }
}