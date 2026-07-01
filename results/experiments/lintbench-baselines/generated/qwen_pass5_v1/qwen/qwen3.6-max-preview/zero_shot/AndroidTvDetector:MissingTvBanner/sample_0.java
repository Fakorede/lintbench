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

import java.util.Collection;
import java.util.Collections;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    public static final Issue ISSUE = Issue.create(
        "MissingTvBanner",
        "TV Missing Banner",
        "A TV application must provide a home screen banner for each localization if it includes a Leanback launcher intent filter. The banner is the app launch point that appears on the home screen in the apps and games rows.",
        Category.CORRECTNESS,
        5,
        Severity.ERROR,
        new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    private static final String LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER";
    private static final String TAG_CATEGORY = "category";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_APPLICATION = "application";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_BANNER = "banner";

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_CATEGORY);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (!LEANBACK_LAUNCHER.equals(name)) {
            return;
        }

        Node current = element.getParentNode();
        Element target = null;
        while (current != null) {
            if (current.getNodeType() == Node.ELEMENT_NODE) {
                String tag = ((Element) current).getTagName();
                if (TAG_ACTIVITY.equals(tag) || TAG_APPLICATION.equals(tag)) {
                    target = (Element) current;
                    break;
                }
            }
            current = current.getParentNode();
        }

        if (target == null) {
            return;
        }

        if (hasBanner(target)) {
            return;
        }

        if (TAG_ACTIVITY.equals(target.getTagName())) {
            Element application = getApplicationElement(context);
            if (application != null && hasBanner(application)) {
                return;
            }
        }

        context.report(ISSUE, element, context.getLocation(element),
            "TV applications targeting the Leanback launcher must declare an `android:banner` attribute.");
    }

    private static boolean hasBanner(Element element) {
        return element.hasAttributeNS(ANDROID_URI, ATTR_BANNER);
    }

    private static Element getApplicationElement(XmlContext context) {
        Element root = context.document.getDocumentElement();
        if (root == null) {
            return null;
        }
        NodeList list = root.getElementsByTagName(TAG_APPLICATION);
        if (list.getLength() > 0) {
            return (Element) list.item(0);
        }
        return null;
    }
}