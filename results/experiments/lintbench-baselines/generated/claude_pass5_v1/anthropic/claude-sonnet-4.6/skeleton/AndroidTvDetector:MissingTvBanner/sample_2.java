package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String ANDROID_MANIFEST_XML = "AndroidManifest.xml";

    private static final String TAG_APPLICATION = "application";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_CATEGORY = "category";
    private static final String TAG_ACTION = "action";

    private static final String ATTR_BANNER = "banner";
    private static final String ATTR_NAME = "name";

    private static final String LEANBACK_LAUNCHER_CATEGORY =
            "android.intent.category.LEANBACK_LAUNCHER";

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "MissingTvBanner",
                    "TV Missing Banner",
                    "A TV application must provide a home screen banner for each localization "
                            + "if it includes a Leanback launcher intent filter. "
                            + "The banner is the app launch point that appears on the home "
                            + "screen in the apps and games rows.",
                    Category.CORRECTNESS,
                    7,
                    Severity.ERROR,
                    IMPLEMENTATION);

    /** Whether the current manifest file has a Leanback launcher intent filter */
    private boolean mHasLeanbackLauncherIntent;

    /** Whether the application element has a banner attribute */
    private boolean mApplicationHasBanner;

    /** The application element, for error reporting */
    private Element mApplicationElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_APPLICATION);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mHasLeanbackLauncherIntent = false;
        mApplicationHasBanner = false;
        mApplicationElement = null;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mHasLeanbackLauncherIntent && !mApplicationHasBanner) {
            if (mApplicationElement != null && context instanceof XmlContext) {
                XmlContext xmlContext = (XmlContext) context;
                xmlContext.report(
                        ISSUE,
                        mApplicationElement,
                        xmlContext.getLocation(mApplicationElement),
                        "A TV application must provide a home screen banner (`android:banner`) "
                                + "in the `<application>` element, or in each "
                                + "`<activity>` with a Leanback launcher intent filter.");
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Only process manifest files
        String fileName = context.file.getName();
        if (!ANDROID_MANIFEST_XML.equals(fileName)) {
            return;
        }

        if (!TAG_APPLICATION.equals(element.getTagName())) {
            return;
        }

        mApplicationElement = element;

        // Check if application element has a banner attribute
        Attr bannerAttr = element.getAttributeNodeNS(ANDROID_NS, ATTR_BANNER);
        if (bannerAttr != null && !bannerAttr.getValue().isEmpty()) {
            mApplicationHasBanner = true;
        }

        // Check child activities for leanback launcher intent filters
        // and whether they have banners if the application doesn't
        checkActivitiesForLeanbackLauncher(context, element);
    }

    private void checkActivitiesForLeanbackLauncher(
            @NonNull XmlContext context, @NonNull Element applicationElement) {
        NodeList children = applicationElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            String tagName = childElement.getTagName();
            if (TAG_ACTIVITY.equals(tagName)) {
                checkActivityForLeanbackLauncher(context, childElement);
            }
        }
    }

    private void checkActivityForLeanbackLauncher(
            @NonNull XmlContext context, @NonNull Element activityElement) {
        boolean activityHasLeanbackLauncher = false;
        boolean activityHasBanner = false;

        // Check if activity has a banner
        Attr bannerAttr = activityElement.getAttributeNodeNS(ANDROID_NS, ATTR_BANNER);
        if (bannerAttr != null && !bannerAttr.getValue().isEmpty()) {
            activityHasBanner = true;
        }

        // Check intent filters for leanback launcher
        NodeList children = activityElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (TAG_INTENT_FILTER.equals(childElement.getTagName())) {
                if (intentFilterHasLeanbackLauncher(childElement)) {
                    activityHasLeanbackLauncher = true;
                    break;
                }
            }
        }

        if (activityHasLeanbackLauncher) {
            mHasLeanbackLauncherIntent = true;
            // If the application doesn't have a banner, check if this activity has one
            if (!mApplicationHasBanner && !activityHasBanner) {
                // We'll report at afterCheckFile level if no banner found at application level
                // But also check per-activity banner
                // The report will be done in afterCheckFile for the application element
            }
        }
    }

    private boolean intentFilterHasLeanbackLauncher(@NonNull Element intentFilterElement) {
        NodeList children = intentFilterElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (TAG_CATEGORY.equals(childElement.getTagName())) {
                String nameAttr = childElement.getAttributeNS(ANDROID_NS, ATTR_NAME);
                if (LEANBACK_LAUNCHER_CATEGORY.equals(nameAttr)) {
                    return true;
                }
            }
        }
        return false;
    }
}