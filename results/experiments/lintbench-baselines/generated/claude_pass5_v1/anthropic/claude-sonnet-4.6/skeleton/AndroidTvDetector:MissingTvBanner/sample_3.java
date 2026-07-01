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
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

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
                            + "if it includes a Leanback launcher intent filter. The banner is the "
                            + "app launch point that appears on the home screen in the apps and "
                            + "games rows.",
                    Category.CORRECTNESS,
                    7,
                    Severity.ERROR,
                    IMPLEMENTATION);

    /** Whether the current manifest has a Leanback launcher intent filter */
    private boolean mHasLeanbackLauncherIntent;

    /** Whether the current manifest has a banner defined at the application level */
    private boolean mHasApplicationBanner;

    /** The application element for reporting issues */
    private Element mApplicationElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_APPLICATION, TAG_ACTIVITY);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mHasLeanbackLauncherIntent = false;
        mHasApplicationBanner = false;
        mApplicationElement = null;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mHasLeanbackLauncherIntent && !mHasApplicationBanner) {
            if (mApplicationElement != null && context instanceof XmlContext) {
                XmlContext xmlContext = (XmlContext) context;
                xmlContext.report(
                        ISSUE,
                        mApplicationElement,
                        xmlContext.getLocation(mApplicationElement),
                        "A TV application must provide a home screen banner (`android:banner`) "
                                + "in the `<application>` element or in each `<activity>` that "
                                + "handles the `LEANBACK_LAUNCHER` intent.");
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        if (TAG_APPLICATION.equals(tagName)) {
            mApplicationElement = element;
            String banner = element.getAttributeNS(ANDROID_NS, ATTR_BANNER);
            if (banner != null && !banner.isEmpty()) {
                mHasApplicationBanner = true;
            }
            // Check if any activity within the application has a leanback launcher
            checkForLeanbackLauncher(element);
        } else if (TAG_ACTIVITY.equals(tagName)) {
            if (hasLeanbackLauncherIntentFilter(element)) {
                mHasLeanbackLauncherIntent = true;
                // Check if this activity has a banner
                String banner = element.getAttributeNS(ANDROID_NS, ATTR_BANNER);
                if ((banner == null || banner.isEmpty()) && !mHasApplicationBanner) {
                    // Will be reported in afterCheckFile if no application banner either
                }
            }
        }
    }

    /**
     * Checks whether the application element contains any activity with a leanback launcher
     * intent filter.
     */
    private void checkForLeanbackLauncher(@NonNull Element applicationElement) {
        NodeList children = applicationElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (TAG_ACTIVITY.equals(childElement.getTagName())) {
                    if (hasLeanbackLauncherIntentFilter(childElement)) {
                        mHasLeanbackLauncherIntent = true;
                        return;
                    }
                }
            }
        }
    }

    /**
     * Returns true if the given element contains an intent-filter with the LEANBACK_LAUNCHER
     * category.
     */
    private boolean hasLeanbackLauncherIntentFilter(@NonNull Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (TAG_INTENT_FILTER.equals(childElement.getTagName())) {
                    if (intentFilterHasLeanbackCategory(childElement)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Returns true if the given intent-filter element contains a category element with
     * the LEANBACK_LAUNCHER category name.
     */
    private boolean intentFilterHasLeanbackCategory(@NonNull Element intentFilterElement) {
        NodeList children = intentFilterElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (TAG_CATEGORY.equals(childElement.getTagName())) {
                    String name = childElement.getAttributeNS(ANDROID_NS, ATTR_NAME);
                    if (LEANBACK_LAUNCHER_CATEGORY.equals(name)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}