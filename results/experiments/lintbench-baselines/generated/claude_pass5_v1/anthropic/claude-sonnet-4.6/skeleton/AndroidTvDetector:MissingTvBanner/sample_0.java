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
                            + "if it includes a Leanback launcher intent filter. The banner is "
                            + "the app launch point that appears on the home screen in the apps "
                            + "and games rows.",
                    Category.CORRECTNESS,
                    7,
                    Severity.ERROR,
                    IMPLEMENTATION);

    /** Whether the current file is the Android manifest */
    private boolean mIsManifest;

    /** Whether we found a Leanback launcher intent filter */
    private boolean mHasLeanbackLauncher;

    /** Whether the application has a banner defined */
    private boolean mHasApplicationBanner;

    /** The application element, for reporting location */
    private Element mApplicationElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_APPLICATION, TAG_ACTIVITY);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIsManifest = ANDROID_MANIFEST_XML.equals(context.file.getName());
        mHasLeanbackLauncher = false;
        mHasApplicationBanner = false;
        mApplicationElement = null;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!mIsManifest) {
            return;
        }

        if (mHasLeanbackLauncher && !mHasApplicationBanner) {
            if (context instanceof XmlContext && mApplicationElement != null) {
                XmlContext xmlContext = (XmlContext) context;
                xmlContext.report(
                        ISSUE,
                        mApplicationElement,
                        xmlContext.getLocation(mApplicationElement),
                        "A TV application must provide a home screen banner (`android:banner`) "
                                + "for each localization. The banner is the app launch point "
                                + "that appears on the home screen in the apps and games rows.");
            } else {
                context.report(
                        ISSUE,
                        com.android.tools.lint.detector.api.Location.create(context.file),
                        "A TV application must provide a home screen banner (`android:banner`) "
                                + "for each localization. The banner is the app launch point "
                                + "that appears on the home screen in the apps and games rows.");
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!mIsManifest) {
            return;
        }

        String tagName = element.getTagName();

        if (TAG_APPLICATION.equals(tagName)) {
            mApplicationElement = element;

            // Check if the application element has a banner attribute
            String banner = element.getAttributeNS(ANDROID_NS, ATTR_BANNER);
            if (banner != null && !banner.isEmpty()) {
                mHasApplicationBanner = true;
            }

            // Also check all activities for leanback launcher
            checkElementForLeanbackLauncher(element);

        } else if (TAG_ACTIVITY.equals(tagName)) {
            // Check if this activity has a leanback launcher intent filter
            if (hasLeanbackLauncherIntentFilter(element)) {
                mHasLeanbackLauncher = true;

                // Check if the activity itself has a banner (overrides application banner)
                String banner = element.getAttributeNS(ANDROID_NS, ATTR_BANNER);
                if (banner != null && !banner.isEmpty()) {
                    mHasApplicationBanner = true;
                }
            }
        }
    }

    /**
     * Checks whether an element (application or activity) contains a Leanback launcher intent
     * filter.
     */
    private void checkElementForLeanbackLauncher(@NonNull Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (TAG_ACTIVITY.equals(childElement.getTagName())) {
                    if (hasLeanbackLauncherIntentFilter(childElement)) {
                        mHasLeanbackLauncher = true;

                        // Check if the activity has its own banner
                        String banner = childElement.getAttributeNS(ANDROID_NS, ATTR_BANNER);
                        if (banner != null && !banner.isEmpty()) {
                            mHasApplicationBanner = true;
                        }
                    }
                }
            }
        }
    }

    /** Returns true if the given element has an intent-filter with the Leanback launcher category */
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

    /** Returns true if the given intent-filter element has the Leanback launcher category */
    private boolean intentFilterHasLeanbackCategory(@NonNull Element intentFilter) {
        NodeList children = intentFilter.getChildNodes();
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