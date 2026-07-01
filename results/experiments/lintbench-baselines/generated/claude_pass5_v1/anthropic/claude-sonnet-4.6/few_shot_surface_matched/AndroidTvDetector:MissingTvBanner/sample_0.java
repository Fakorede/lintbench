package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_BANNER;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.xml.AndroidManifest.NODE_ACTION;
import static com.android.xml.AndroidManifest.NODE_ACTIVITY;
import static com.android.xml.AndroidManifest.NODE_APPLICATION;
import static com.android.xml.AndroidManifest.NODE_CATEGORY;
import static com.android.xml.AndroidManifest.NODE_INTENT;

import com.android.annotations.NonNull;
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

    private static final String LEANBACK_LAUNCHER_CATEGORY =
            "android.intent.category.LEANBACK_LAUNCHER";
    private static final String ACTION_MAIN = "android.intent.action.MAIN";

    public static final Issue ISSUE =
            Issue.create(
                    "MissingTvBanner",
                    "TV Missing Banner",
                    "A TV application must provide a home screen banner for each localization "
                            + "if it includes a Leanback launcher intent filter. "
                            + "The banner is the app launch point that appears on the home "
                            + "screen in the apps and games rows.",
                    Category.CORRECTNESS,
                    8,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    /** Whether the manifest has a Leanback launcher intent filter */
    private boolean mHasLeanbackLauncherIntent;

    /** Whether the application or any activity has a banner defined */
    private boolean mHasBanner;

    /** The application element, used for reporting location */
    private Element mApplicationElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(NODE_APPLICATION, NODE_ACTIVITY);
    }

    @Override
    public void beforeCheckFile(@NonNull com.android.tools.lint.detector.api.Context context) {
        mHasLeanbackLauncherIntent = false;
        mHasBanner = false;
        mApplicationElement = null;
    }

    @Override
    public void afterCheckFile(@NonNull com.android.tools.lint.detector.api.Context context) {
        if (mHasLeanbackLauncherIntent && !mHasBanner) {
            XmlContext xmlContext = (XmlContext) context;
            if (mApplicationElement != null) {
                xmlContext.report(
                        ISSUE,
                        mApplicationElement,
                        xmlContext.getLocation(mApplicationElement),
                        "TV apps must include a home screen banner for the app or each activity "
                                + "that is enabled for Android TV; "
                                + "see https://developer.android.com/training/tv/start/start.html#banner");
            } else {
                xmlContext.report(
                        ISSUE,
                        xmlContext.getLocation(xmlContext.document),
                        "TV apps must include a home screen banner for the app or each activity "
                                + "that is enabled for Android TV; "
                                + "see https://developer.android.com/training/tv/start/start.html#banner");
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        if (NODE_APPLICATION.equals(tagName)) {
            mApplicationElement = element;

            // Check if the application element itself has a banner
            String banner = element.getAttributeNS(ANDROID_URI, ATTR_BANNER);
            if (banner != null && !banner.isEmpty()) {
                mHasBanner = true;
            }

            // Check all child activities for leanback launcher intent filters
            checkForLeanbackLauncher(element);

        } else if (NODE_ACTIVITY.equals(tagName)) {
            // Check if this activity has a banner
            String banner = element.getAttributeNS(ANDROID_URI, ATTR_BANNER);
            if (banner != null && !banner.isEmpty()) {
                // Only counts if this activity also has a leanback launcher
                if (activityHasLeanbackLauncher(element)) {
                    mHasBanner = true;
                }
            }

            // Check if this activity has a leanback launcher intent filter
            if (activityHasLeanbackLauncher(element)) {
                mHasLeanbackLauncherIntent = true;
            }
        }
    }

    /**
     * Checks the application element and all its children for leanback launcher intent filters.
     */
    private void checkForLeanbackLauncher(@NonNull Element applicationElement) {
        NodeList children = applicationElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (NODE_ACTIVITY.equals(childElement.getTagName())) {
                    if (activityHasLeanbackLauncher(childElement)) {
                        mHasLeanbackLauncherIntent = true;
                    }
                }
            }
        }
    }

    /**
     * Returns true if the given activity element contains an intent-filter with both
     * ACTION_MAIN and LEANBACK_LAUNCHER category.
     */
    private boolean activityHasLeanbackLauncher(@NonNull Element activityElement) {
        NodeList children = activityElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (NODE_INTENT.equals(childElement.getTagName())) {
                    if (intentFilterHasLeanbackLauncher(childElement)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Returns true if the given intent-filter element has both ACTION_MAIN action
     * and LEANBACK_LAUNCHER category.
     */
    private boolean intentFilterHasLeanbackLauncher(@NonNull Element intentFilterElement) {
        boolean hasMainAction = false;
        boolean hasLeanbackCategory = false;

        NodeList children = intentFilterElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                String tagName = childElement.getTagName();
                String name = childElement.getAttributeNS(ANDROID_URI, ATTR_NAME);

                if (NODE_ACTION.equals(tagName)) {
                    if (ACTION_MAIN.equals(name)) {
                        hasMainAction = true;
                    }
                } else if (NODE_CATEGORY.equals(tagName)) {
                    if (LEANBACK_LAUNCHER_CATEGORY.equals(name)) {
                        hasLeanbackCategory = true;
                    }
                }
            }
        }

        return hasMainAction && hasLeanbackCategory;
    }
}