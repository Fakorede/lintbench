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

/**
 * Detector for TV applications that are missing a home screen banner.
 */
public class AndroidTvDetector extends Detector implements XmlScanner {

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
            )
    ).addMoreInfo("https://developer.android.com/training/tv/start/start.html#banner");

    private static final String ANDROID_MANIFEST = "AndroidManifest.xml";
    private static final String TAG_APPLICATION = "application";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_CATEGORY = "category";
    private static final String ATTR_BANNER = "banner";
    private static final String ATTR_NAME = "name";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String LEANBACK_LAUNCHER_CATEGORY =
            "android.intent.category.LEANBACK_LAUNCHER";

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

        // Check if the application has a banner defined
        String banner = element.getAttributeNS(ANDROID_NS, ATTR_BANNER);
        if (banner != null && !banner.isEmpty()) {
            // Application-level banner is defined, we're good
            return;
        }

        // Check if all activities with Leanback launcher have a banner
        // If the application doesn't have a banner, each leanback activity must have one
        boolean allLeanbackActivitiesHaveBanner = true;
        boolean foundLeanbackActivity = false;
        Element missingBannerActivity = null;

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (!childElement.getTagName().equals(TAG_ACTIVITY)) {
                continue;
            }

            if (hasLeanbackLauncherIntentFilter(childElement)) {
                foundLeanbackActivity = true;
                String activityBanner = childElement.getAttributeNS(ANDROID_NS, ATTR_BANNER);
                if (activityBanner == null || activityBanner.isEmpty()) {
                    allLeanbackActivitiesHaveBanner = false;
                    missingBannerActivity = childElement;
                }
            }
        }

        if (foundLeanbackActivity && !allLeanbackActivitiesHaveBanner) {
            if (missingBannerActivity != null) {
                context.report(
                        MISSING_BANNER,
                        missingBannerActivity,
                        context.getLocation(missingBannerActivity),
                        "A TV application must provide a home screen banner (`android:banner`) " +
                        "for the activity with the Leanback launcher intent filter, or for the " +
                        "application element."
                );
            } else {
                context.report(
                        MISSING_BANNER,
                        element,
                        context.getLocation(element),
                        "A TV application must provide a home screen banner (`android:banner`) " +
                        "for the activity with the Leanback launcher intent filter, or for the " +
                        "application element."
                );
            }
        }
    }

    /**
     * Checks if the application element contains any activity with a Leanback launcher
     * intent filter.
     */
    private boolean hasLeanbackLauncherActivity(Element applicationElement) {
        NodeList children = applicationElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (childElement.getTagName().equals(TAG_ACTIVITY)) {
                if (hasLeanbackLauncherIntentFilter(childElement)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks if an activity element has a Leanback launcher intent filter.
     */
    private boolean hasLeanbackLauncherIntentFilter(Element activityElement) {
        NodeList children = activityElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (childElement.getTagName().equals(TAG_INTENT_FILTER)) {
                if (hasLeanbackLauncherCategory(childElement)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks if an intent-filter element contains the Leanback launcher category.
     */
    private boolean hasLeanbackLauncherCategory(Element intentFilterElement) {
        NodeList children = intentFilterElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (childElement.getTagName().equals(TAG_CATEGORY)) {
                String name = childElement.getAttributeNS(ANDROID_NS, ATTR_NAME);
                if (LEANBACK_LAUNCHER_CATEGORY.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }
}