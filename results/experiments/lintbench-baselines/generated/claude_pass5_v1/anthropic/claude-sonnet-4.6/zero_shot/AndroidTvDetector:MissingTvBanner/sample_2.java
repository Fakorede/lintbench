package com.android.tools.lint.checks;

import com.android.SdkConstants;
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

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String LEANBACK_LAUNCHER =
            "android.intent.category.LEANBACK_LAUNCHER";

    private static final String ANDROID_BANNER = "banner";

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
                    Scope.MANIFEST_SCOPE
            ))
            .addMoreInfo("https://developer.android.com/training/tv/start/start.html#banner");

    public AndroidTvDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(SdkConstants.TAG_APPLICATION);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!SdkConstants.TAG_APPLICATION.equals(element.getTagName())) {
            return;
        }

        // Check if the application has a Leanback launcher intent filter
        if (!hasLeanbackLauncherIntentFilter(element)) {
            return;
        }

        // Check if the application has a banner defined
        Attr bannerAttr = element.getAttributeNodeNS(
                SdkConstants.ANDROID_URI, ANDROID_BANNER);

        if (bannerAttr == null || bannerAttr.getValue().isEmpty()) {
            context.report(
                    MISSING_BANNER,
                    element,
                    context.getLocation(element),
                    "A TV application must provide a home screen banner (`android:banner`) " +
                    "for each localization when it includes a Leanback launcher intent filter."
            );
        }
    }

    /**
     * Checks whether the given application element contains an activity with a
     * Leanback launcher intent filter.
     */
    private boolean hasLeanbackLauncherIntentFilter(Element applicationElement) {
        NodeList children = applicationElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            String tagName = childElement.getTagName();

            // Check activity, activity-alias, and service elements
            if (SdkConstants.TAG_ACTIVITY.equals(tagName)
                    || "activity-alias".equals(tagName)
                    || SdkConstants.TAG_SERVICE.equals(tagName)) {
                if (hasLeanbackLauncherCategory(childElement)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks whether the given component element contains an intent-filter
     * with the LEANBACK_LAUNCHER category.
     */
    private boolean hasLeanbackLauncherCategory(Element componentElement) {
        NodeList children = componentElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (SdkConstants.TAG_INTENT_FILTER.equals(childElement.getTagName())) {
                if (intentFilterHasLeanbackCategory(childElement)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks whether the given intent-filter element contains a category
     * element with the LEANBACK_LAUNCHER category name.
     */
    private boolean intentFilterHasLeanbackCategory(Element intentFilterElement) {
        NodeList children = intentFilterElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (SdkConstants.TAG_CATEGORY.equals(childElement.getTagName())) {
                String categoryName = childElement.getAttributeNS(
                        SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                if (LEANBACK_LAUNCHER.equals(categoryName)) {
                    return true;
                }
            }
        }
        return false;
    }
}