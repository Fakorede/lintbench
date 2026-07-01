package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
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

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER";

    public static final Issue ISSUE = Issue.create(
            "MissingTvBanner",
            "TV Missing Banner",
            "A TV application must provide a home screen banner for each localization if it includes a Leanback launcher intent filter. "
                    + "The banner is the app launch point that appears on the home screen in the apps and games rows.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private Element mApplicationElement;
    private boolean mHasLeanbackLauncher;
    private boolean mHasApplicationBanner;
    private boolean mHasLeanbackActivityBanner;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("application", "activity");
    }

    @Override
    public void beforeCheckFile(Context context) {
        mApplicationElement = null;
        mHasLeanbackLauncher = false;
        mHasApplicationBanner = false;
        mHasLeanbackActivityBanner = false;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        if ("application".equals(tag)) {
            mApplicationElement = element;
            if (element.hasAttributeNS(ANDROID_URI, "banner")) {
                mHasApplicationBanner = true;
            }
        } else if ("activity".equals(tag)) {
            boolean hasBanner = element.hasAttributeNS(ANDROID_URI, "banner");
            if (hasLeanbackIntentFilter(element)) {
                mHasLeanbackLauncher = true;
                if (hasBanner) {
                    mHasLeanbackActivityBanner = true;
                }
            }
        }
    }

    private boolean hasLeanbackIntentFilter(Element activity) {
        NodeList children = activity.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "intent-filter".equals(child.getNodeName())) {
                Element intentFilter = (Element) child;
                NodeList categories = intentFilter.getElementsByTagName("category");
                for (int j = 0; j < categories.getLength(); j++) {
                    Node categoryNode = categories.item(j);
                    if (categoryNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element category = (Element) categoryNode;
                        String name = category.getAttributeNS(ANDROID_URI, "name");
                        if (LEANBACK_LAUNCHER.equals(name)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void afterCheckFile(Context context) {
        if (mHasLeanbackLauncher && !mHasApplicationBanner && !mHasLeanbackActivityBanner) {
            XmlContext xmlContext = (XmlContext) context;
            Element locationElement = mApplicationElement != null ? mApplicationElement : xmlContext.getDocument().getDocumentElement();
            context.report(ISSUE, xmlContext.getLocation(locationElement),
                    "TV app must provide a home screen banner if it includes a Leanback launcher intent filter");
        }
    }
}