package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
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

/**
 * Checks for missing TV banner in applications that include a Leanback launcher intent filter.
 */
public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String LEANBACK_LAUNCHER_CATEGORY =
            "android.intent.category.LEANBACK_LAUNCHER";
    private static final String ACTION_MAIN = "android.intent.action.MAIN";
    private static final String TAG_APPLICATION = "application";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_CATEGORY = "category";
    private static final String TAG_ACTION = "action";
    private static final String ATTR_BANNER = "banner";
    private static final String ATTR_NAME = "name";

    /** Issue: Missing TV Banner */
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

    /** Constructs a new {@link AndroidTvDetector} */
    public AndroidTvDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_APPLICATION);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!TAG_APPLICATION.equals(element.getTagName())) {
            return;
        }

        // Check if the application has any activity with a Leanback launcher intent filter
        if (!hasLeanbackLauncherActivity(element)) {
            return;
        }

        // Check if the application has a banner defined
        String banner = element.getAttributeNS(SdkConstants.ANDROID_URI, ATTR_BANNER);
        if (banner != null && !banner.isEmpty()) {
            // Application-level banner is defined, we're good
            return;
        }

        // Check if the activity with the Leanback launcher has a banner
        // Find the activity with the leanback launcher and check if it has a banner
        Element leanbackActivity = findLeanbackLauncherActivity(element);
        if (leanbackActivity != null) {
            String activityBanner = leanbackActivity.getAttributeNS(
                    SdkConstants.ANDROID_URI, ATTR_BANNER);
            if (activityBanner != null && !activityBanner.isEmpty()) {
                // Activity-level banner is defined, we're good
                return;
            }
        }

        // No banner found - report the issue
        context.report(
                MISSING_BANNER,
                element,
                context.getLocation(element),
                "A TV application must provide a home screen banner (`android:banner`) " +
                "for each localization if it includes a Leanback launcher intent filter"
        );
    }

    /**
     * Checks if the application element has any activity with a Leanback launcher intent filter.
     */
    private boolean hasLeanbackLauncherActivity(Element applicationElement) {
        return findLeanbackLauncherActivity(applicationElement) != null;
    }

    /**
     * Finds the activity element that contains a Leanback launcher intent filter.
     *
     * @return the activity element, or null if not found
     */
    private Element findLeanbackLauncherActivity(Element applicationElement) {
        NodeList children = applicationElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (!TAG_ACTIVITY.equals(childElement.getTagName())) {
                continue;
            }
            // Check if this activity has a Leanback launcher intent filter
            if (hasLeanbackLauncherIntentFilter(childElement)) {
                return childElement;
            }
        }
        return null;
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
            if (!TAG_INTENT_FILTER.equals(childElement.getTagName())) {
                continue;
            }
            if (intentFilterHasLeanbackLauncher(childElement)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if an intent-filter element has the LEANBACK_LAUNCHER category
     * and the MAIN action.
     */
    private boolean intentFilterHasLeanbackLauncher(Element intentFilterElement) {
        boolean hasMainAction = false;
        boolean hasLeanbackCategory = false;

        NodeList children = intentFilterElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            String tagName = childElement.getTagName();

            if (TAG_ACTION.equals(tagName)) {
                String actionName = childElement.getAttributeNS(
                        SdkConstants.ANDROID_URI, ATTR_NAME);
                if (ACTION_MAIN.equals(actionName)) {
                    hasMainAction = true;
                }
            } else if (TAG_CATEGORY.equals(tagName)) {
                String categoryName = childElement.getAttributeNS(
                        SdkConstants.ANDROID_URI, ATTR_NAME);
                if (LEANBACK_LAUNCHER_CATEGORY.equals(categoryName)) {
                    hasLeanbackCategory = true;
                }
            }
        }

        return hasMainAction && hasLeanbackCategory;
    }
}