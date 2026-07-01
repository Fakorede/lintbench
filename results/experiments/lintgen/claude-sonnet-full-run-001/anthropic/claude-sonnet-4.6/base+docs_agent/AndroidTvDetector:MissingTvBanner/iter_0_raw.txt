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

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.xml.AndroidManifest.NODE_ACTION;
import static com.android.xml.AndroidManifest.NODE_CATEGORY;
import static com.android.xml.AndroidManifest.NODE_INTENT;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String LEANBACK_LAUNCHER_CATEGORY =
            "android.intent.category.LEANBACK_LAUNCHER";
    private static final String ATTR_BANNER = "banner";

    public static final Issue MISSING_BANNER = Issue.create(
            "MissingTvBanner",
            "TV Missing Banner",
            "A TV application must provide a home screen banner for each localization if it " +
            "includes a Leanback launcher intent filter. The banner is the app launch point " +
            "that appears on the home screen in the apps and games rows.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(
                    AndroidTvDetector.class,
                    Scope.MANIFEST_SCOPE));

    public AndroidTvDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_APPLICATION);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!element.getTagName().equals(TAG_APPLICATION)) {
            return;
        }

        // Check if any activity has a Leanback launcher intent filter
        if (!hasLeanbackLauncherActivity(element)) {
            return;
        }

        // Check if the application or any activity with leanback launcher has a banner
        if (!hasBanner(element)) {
            context.report(
                    MISSING_BANNER,
                    element,
                    context.getLocation(element),
                    "A TV application must provide a home screen banner (`android:banner`) " +
                    "for each localization if it includes a Leanback launcher intent filter.");
        }
    }

    private boolean hasLeanbackLauncherActivity(Element applicationElement) {
        NodeList children = applicationElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (TAG_ACTIVITY.equals(childElement.getTagName())) {
                if (hasLeanbackLauncherIntentFilter(childElement)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasLeanbackLauncherIntentFilter(Element activityElement) {
        NodeList children = activityElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (NODE_INTENT.equals(childElement.getTagName())) {
                if (intentFilterHasLeanbackCategory(childElement)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean intentFilterHasLeanbackCategory(Element intentFilterElement) {
        NodeList children = intentFilterElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (NODE_CATEGORY.equals(childElement.getTagName())) {
                String name = childElement.getAttributeNS(ANDROID_URI, "name");
                if (LEANBACK_LAUNCHER_CATEGORY.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasBanner(Element applicationElement) {
        // Check if the application element itself has a banner
        String appBanner = applicationElement.getAttributeNS(ANDROID_URI, ATTR_BANNER);
        if (appBanner != null && !appBanner.isEmpty()) {
            return true;
        }

        // Check if any activity with a leanback launcher intent filter has a banner
        NodeList children = applicationElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (TAG_ACTIVITY.equals(childElement.getTagName())) {
                if (hasLeanbackLauncherIntentFilter(childElement)) {
                    String activityBanner = childElement.getAttributeNS(ANDROID_URI, ATTR_BANNER);
                    if (activityBanner != null && !activityBanner.isEmpty()) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}