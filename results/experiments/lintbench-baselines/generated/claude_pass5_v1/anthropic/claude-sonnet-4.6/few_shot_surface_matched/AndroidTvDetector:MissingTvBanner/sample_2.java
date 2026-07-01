package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_BANNER;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.xml.AndroidManifest.NODE_ACTION;
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

    private static final String LEANBACK_LAUNCHER_CATEGORY =
            "android.intent.category.LEANBACK_LAUNCHER";

    public static final Issue MISSING_BANNER =
            Issue.create(
                    "MissingTvBanner",
                    "Missing TV Banner",
                    "A TV application must provide a home screen banner for each localization "
                            + "if it includes a Leanback launcher intent filter. "
                            + "The banner is the app launch point that appears on the home "
                            + "screen in the apps and games rows.",
                    Category.CORRECTNESS,
                    8,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private boolean mHasLeanbackLauncherIntent;
    private boolean mApplicationHasBanner;
    private Element mApplicationElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(NODE_APPLICATION, NODE_INTENT, NODE_ACTION, NODE_CATEGORY);
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
                        MISSING_BANNER,
                        mApplicationElement,
                        xmlContext.getLocation(mApplicationElement),
                        "A TV application must provide a home screen banner (`android:banner`) "
                                + "for each localization if it includes a Leanback launcher "
                                + "intent filter");
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
        } else if (NODE_CATEGORY.equals(tagName)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (LEANBACK_LAUNCHER_CATEGORY.equals(name)) {
                mHasLeanbackLauncherIntent = true;

                // Check if the activity containing this intent-filter has a banner
                // Walk up: category -> intent-filter -> activity
                Node intentFilter = element.getParentNode();
                if (intentFilter != null) {
                    Node activityNode = intentFilter.getParentNode();
                    if (activityNode instanceof Element) {
                        Element activity = (Element) activityNode;
                        String activityBanner = activity.getAttributeNS(ANDROID_URI, ATTR_BANNER);
                        if (activityBanner != null && !activityBanner.isEmpty()) {
                            // Activity has its own banner, that's fine
                            mApplicationHasBanner = true;
                        }
                    }
                }
            }
        }
    }
}