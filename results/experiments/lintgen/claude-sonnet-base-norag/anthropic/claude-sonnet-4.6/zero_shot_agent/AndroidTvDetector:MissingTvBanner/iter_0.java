package com.android.tools.lint.checks;

import com.android.SdkConstants;
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

/**
 * Checks for Android TV specific issues in the manifest.
 */
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
                    Scope.MANIFEST_SCOPE))
            .addMoreInfo("https://developer.android.com/training/tv/start/start.html#banner");

    /** Constructs a new {@link AndroidTvDetector} */
    public AndroidTvDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(SdkConstants.TAG_APPLICATION, SdkConstants.TAG_ACTIVITY);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Check if this element has a Leanback launcher intent filter
        if (!hasLeanbackLauncherIntentFilter(element)) {
            return;
        }

        // Check if the element has a banner attribute
        String tagName = element.getTagName();
        if (SdkConstants.TAG_APPLICATION.equals(tagName) ||
                SdkConstants.TAG_ACTIVITY.equals(tagName)) {
            Attr bannerAttr = element.getAttributeNodeNS(
                    SdkConstants.ANDROID_URI, ATTR_BANNER);
            if (bannerAttr == null) {
                // No banner attribute found - report the issue
                context.report(
                        MISSING_BANNER,
                        element,
                        context.getNameLocation(element),
                        "A TV application must provide a home screen banner (`android:banner`) " +
                        "for each localization if it includes a Leanback launcher intent filter");
            }
        }
    }

    /**
     * Checks whether the given element contains an intent-filter child element
     * that includes the LEANBACK_LAUNCHER category.
     */
    private static boolean hasLeanbackLauncherIntentFilter(Element element) {
        NodeList children = element.getChildNodes();
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
     * Checks whether the given intent-filter element contains a category element
     * with the LEANBACK_LAUNCHER name.
     */
    private static boolean intentFilterHasLeanbackCategory(Element intentFilter) {
        NodeList children = intentFilter.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (SdkConstants.TAG_CATEGORY.equals(childElement.getTagName())) {
                String name = childElement.getAttributeNS(
                        SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                if (LEANBACK_LAUNCHER_CATEGORY.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }
}