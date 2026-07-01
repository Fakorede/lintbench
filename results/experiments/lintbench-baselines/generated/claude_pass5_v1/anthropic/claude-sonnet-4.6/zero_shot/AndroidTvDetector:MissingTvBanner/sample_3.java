package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_BANNER;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.xml.AndroidManifest.ATTRIBUTE_CATEGORY;
import static com.android.xml.AndroidManifest.NODE_ACTION;
import static com.android.xml.AndroidManifest.NODE_APPLICATION;
import static com.android.xml.AndroidManifest.NODE_CATEGORY;
import static com.android.xml.AndroidManifest.NODE_INTENT;

/**
 * Checks for Android TV specific issues in the manifest.
 */
public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String LEANBACK_LAUNCHER_CATEGORY =
            "android.intent.category.LEANBACK_LAUNCHER";

    private static final String LEANBACK_FEATURE =
            "android.software.leanback";

    private static final String ACTION_MAIN = "android.intent.action.MAIN";

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
                    Scope.MANIFEST_SCOPE))
            .addMoreInfo("https://developer.android.com/training/tv/start/start.html#banner");

    /** Constructs a new {@link AndroidTvDetector} */
    public AndroidTvDetector() {
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Arrays.asList(NODE_APPLICATION);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!NODE_APPLICATION.equals(element.getLocalName())) {
            return;
        }

        // Check if this application has a Leanback launcher intent filter
        boolean hasLeanbackLauncher = hasLeanbackLauncherIntentFilter(element);

        if (!hasLeanbackLauncher) {
            return;
        }

        // Check if the application has a banner defined
        String banner = element.getAttributeNS(ANDROID_URI, ATTR_BANNER);
        if (banner != null && !banner.isEmpty()) {
            // Application-level banner is set, we're fine
            return;
        }

        // Check if the activity with the Leanback launcher has a banner
        // We need to check each activity that has the Leanback launcher
        boolean allLeanbackActivitiesHaveBanners = checkLeanbackActivitiesHaveBanners(element);

        if (!allLeanbackActivitiesHaveBanners) {
            // Report the issue on the application element
            Attr applicationNode = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
            if (applicationNode != null) {
                context.report(
                        MISSING_BANNER,
                        element,
                        context.getLocation(applicationNode),
                        "A TV application must provide a home screen banner (`android:banner`)");
            } else {
                context.report(
                        MISSING_BANNER,
                        element,
                        context.getLocation(element),
                        "A TV application must provide a home screen banner (`android:banner`)");
            }
        }
    }

    /**
     * Returns true if the application element contains any activity with a Leanback launcher
     * intent filter.
     */
    private static boolean hasLeanbackLauncherIntentFilter(@NonNull Element applicationElement) {
        NodeList children = applicationElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element activityElement = (Element) child;
            if (activityHasLeanbackLauncher(activityElement)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if the given element (activity, activity-alias, etc.) has a Leanback launcher
     * intent filter.
     */
    private static boolean activityHasLeanbackLauncher(@NonNull Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element intentFilter = (Element) child;
            if (!NODE_INTENT.equals(intentFilter.getLocalName())) {
                continue;
            }
            if (intentFilterHasLeanbackLauncher(intentFilter)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if the given intent-filter element has both the MAIN action and the
     * LEANBACK_LAUNCHER category.
     */
    private static boolean intentFilterHasLeanbackLauncher(@NonNull Element intentFilter) {
        boolean hasMainAction = false;
        boolean hasLeanbackCategory = false;

        NodeList children = intentFilter.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element filterChild = (Element) child;
            String localName = filterChild.getLocalName();
            if (NODE_ACTION.equals(localName)) {
                String actionName = filterChild.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if (ACTION_MAIN.equals(actionName)) {
                    hasMainAction = true;
                }
            } else if (NODE_CATEGORY.equals(localName)) {
                String categoryName = filterChild.getAttributeNS(ANDROID_URI, ATTRIBUTE_CATEGORY);
                if (categoryName == null || categoryName.isEmpty()) {
                    categoryName = filterChild.getAttributeNS(ANDROID_URI, ATTR_NAME);
                }
                if (LEANBACK_LAUNCHER_CATEGORY.equals(categoryName)) {
                    hasLeanbackCategory = true;
                }
            }
        }

        return hasMainAction && hasLeanbackCategory;
    }

    /**
     * Checks whether all activities with Leanback launcher intent filters have a banner defined
     * (either on the activity itself or inherited from the application).
     *
     * @return true if all such activities have banners, false otherwise
     */
    private static boolean checkLeanbackActivitiesHaveBanners(@NonNull Element applicationElement) {
        // Application-level banner
        String appBanner = applicationElement.getAttributeNS(ANDROID_URI, ATTR_BANNER);
        boolean appHasBanner = appBanner != null && !appBanner.isEmpty();

        if (appHasBanner) {
            return true;
        }

        NodeList children = applicationElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element activityElement = (Element) child;
            if (activityHasLeanbackLauncher(activityElement)) {
                String activityBanner = activityElement.getAttributeNS(ANDROID_URI, ATTR_BANNER);
                if (activityBanner == null || activityBanner.isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }
}