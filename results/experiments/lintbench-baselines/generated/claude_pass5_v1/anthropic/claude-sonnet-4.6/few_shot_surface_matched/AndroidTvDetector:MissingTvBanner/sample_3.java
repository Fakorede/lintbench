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
import com.android.tools.lint.detector.api.Context;
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

    public static final Issue ISSUE =
            Issue.create(
                    "MissingTvBanner",
                    "Missing TV Banner",
                    "A TV application must provide a home screen banner for each localization "
                            + "if it includes a Leanback launcher intent filter. "
                            + "The banner is the app launch point that appears on the home "
                            + "screen in the apps and games rows.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final String ACTION_LEANBACK_LAUNCHER =
            "android.intent.action.MAIN";
    private static final String CATEGORY_LEANBACK_LAUNCHER =
            "android.intent.category.LEANBACK_LAUNCHER";

    private boolean mHasLeanbackLauncherIntent;
    private boolean mApplicationHasBanner;
    private Element mApplicationElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                NODE_APPLICATION,
                NODE_ACTIVITY,
                NODE_INTENT
        );
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
            if (mApplicationElement != null) {
                XmlContext xmlContext = (XmlContext) context;
                xmlContext.report(
                        ISSUE,
                        mApplicationElement,
                        xmlContext.getLocation(mApplicationElement),
                        "A TV application must provide a home screen banner (`android:banner`) "
                                + "for the `<application>` element or for each Leanback launcher "
                                + "activity, to be visible in the Android TV launcher");
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        if (NODE_APPLICATION.equals(tagName)) {
            mApplicationElement = element;
            String banner = element.getAttributeNS(ANDROID_URI, ATTR_BANNER);
            if (banner != null && !banner.isEmpty()) {
                mApplicationHasBanner = true;
            }
        } else if (NODE_ACTIVITY.equals(tagName)) {
            // Check if this activity has a leanback launcher intent filter
            if (activityHasLeanbackLauncher(element)) {
                mHasLeanbackLauncherIntent = true;
                // Check if the activity itself has a banner
                // (acceptable alternative to application-level banner)
                String banner = element.getAttributeNS(ANDROID_URI, ATTR_BANNER);
                if (banner != null && !banner.isEmpty()) {
                    mApplicationHasBanner = true;
                }
            }
        } else if (NODE_INTENT.equals(tagName)) {
            // Check intent-filter elements for leanback launcher
            if (intentFilterHasLeanbackLauncher(element)) {
                mHasLeanbackLauncherIntent = true;
            }
        }
    }

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

    private boolean intentFilterHasLeanbackLauncher(@NonNull Element intentFilterElement) {
        boolean hasLeanbackCategory = false;

        NodeList children = intentFilterElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                String tagName = childElement.getTagName();
                if (NODE_CATEGORY.equals(tagName)) {
                    String name = childElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
                    if (CATEGORY_LEANBACK_LAUNCHER.equals(name)) {
                        hasLeanbackCategory = true;
                    }
                }
            }
        }

        return hasLeanbackCategory;
    }
}