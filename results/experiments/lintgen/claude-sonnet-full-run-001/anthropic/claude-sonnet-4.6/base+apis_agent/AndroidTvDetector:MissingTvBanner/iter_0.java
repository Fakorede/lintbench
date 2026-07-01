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
 * Detector for missing TV banner in Android TV applications.
 *
 * <p>A TV application must provide a home screen banner for each localization if it includes a
 * Leanback launcher intent filter.
 */
public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String ANDROID_MANIFEST_XML = "AndroidManifest.xml";
    private static final String TAG_APPLICATION = "application";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_CATEGORY = "category";
    private static final String TAG_ACTION = "action";
    private static final String ATTR_BANNER = "banner";
    private static final String ATTR_NAME = "name";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String LEANBACK_LAUNCHER_CATEGORY =
            "android.intent.category.LEANBACK_LAUNCHER";

    public static final Issue MISSING_BANNER =
            Issue.create(
                    "MissingTvBanner",
                    "TV Missing Banner",
                    "A TV application must provide a home screen banner for each localization if it "
                            + "includes a Leanback launcher intent filter. The banner is the app "
                            + "launch point that appears on the home screen in the apps and games rows.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE))
                    .addMoreInfo(
                            "https://developer.android.com/training/tv/start/start.html#banner");

    /** Constructs a new {@link AndroidTvDetector}. */
    public AndroidTvDetector() {}

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_APPLICATION, TAG_ACTIVITY);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Only process AndroidManifest.xml
        if (!context.file.getName().equals(ANDROID_MANIFEST_XML)) {
            return;
        }

        String tagName = element.getTagName();

        if (TAG_APPLICATION.equals(tagName)) {
            checkApplicationElement(context, element);
        } else if (TAG_ACTIVITY.equals(tagName)) {
            checkActivityElement(context, element);
        }
    }

    /**
     * Checks the <application> element. If the application has a Leanback launcher (via any
     * activity's intent filter), it must have a banner attribute.
     */
    private void checkApplicationElement(XmlContext context, Element application) {
        // Check if any activity within this application has a Leanback launcher intent filter
        if (!hasLeanbackLauncherActivity(application)) {
            return;
        }

        // Check if the application has a banner attribute
        Attr bannerAttr = application.getAttributeNodeNS(ANDROID_NS, ATTR_BANNER);
        if (bannerAttr == null || bannerAttr.getValue().isEmpty()) {
            // Check if all activities with leanback launcher have their own banner
            if (!allLeanbackActivitiesHaveBanner(application)) {
                context.report(
                        MISSING_BANNER,
                        application,
                        context.getNameLocation(application),
                        "A TV application must provide a home screen banner (`android:banner`) "
                                + "for the `<application>` element or each activity with a Leanback "
                                + "launcher intent filter.");
            }
        }
    }

    /**
     * Checks an <activity> element. If the activity has a Leanback launcher intent filter, it
     * should have a banner (either on itself or on the application element).
     */
    private void checkActivityElement(XmlContext context, Element activity) {
        if (!hasLeanbackLauncherIntentFilter(activity)) {
            return;
        }

        // Check if the activity itself has a banner
        Attr activityBanner = activity.getAttributeNodeNS(ANDROID_NS, ATTR_BANNER);
        if (activityBanner != null && !activityBanner.getValue().isEmpty()) {
            return;
        }

        // Check if the parent application element has a banner
        Node parent = activity.getParentNode();
        if (parent instanceof Element) {
            Element parentElement = (Element) parent;
            if (TAG_APPLICATION.equals(parentElement.getTagName())) {
                Attr appBanner = parentElement.getAttributeNodeNS(ANDROID_NS, ATTR_BANNER);
                if (appBanner != null && !appBanner.getValue().isEmpty()) {
                    return;
                }
            }
        }

        // Neither the activity nor the application has a banner
        context.report(
                MISSING_BANNER,
                activity,
                context.getNameLocation(activity),
                "This activity has a Leanback launcher intent filter but does not provide a "
                        + "home screen banner (`android:banner`). Provide the banner on the "
                        + "`<activity>` element or on the parent `<application>` element.");
    }

    /**
     * Returns true if the given application element contains at least one activity with a Leanback
     * launcher intent filter.
     */
    private boolean hasLeanbackLauncherActivity(Element application) {
        NodeList children = application.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                Element childElement = (Element) child;
                if (TAG_ACTIVITY.equals(childElement.getTagName())) {
                    if (hasLeanbackLauncherIntentFilter(childElement)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Returns true if all activities with a Leanback launcher intent filter have their own banner
     * attribute.
     */
    private boolean allLeanbackActivitiesHaveBanner(Element application) {
        NodeList children = application.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                Element childElement = (Element) child;
                if (TAG_ACTIVITY.equals(childElement.getTagName())) {
                    if (hasLeanbackLauncherIntentFilter(childElement)) {
                        Attr banner = childElement.getAttributeNodeNS(ANDROID_NS, ATTR_BANNER);
                        if (banner == null || banner.getValue().isEmpty()) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    /**
     * Returns true if the given activity element contains an intent filter with the Leanback
     * launcher category.
     */
    private boolean hasLeanbackLauncherIntentFilter(Element activity) {
        NodeList children = activity.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
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
     * Returns true if the given intent-filter element contains a category element with the Leanback
     * launcher category name.
     */
    private boolean intentFilterHasLeanbackCategory(Element intentFilter) {
        NodeList children = intentFilter.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
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