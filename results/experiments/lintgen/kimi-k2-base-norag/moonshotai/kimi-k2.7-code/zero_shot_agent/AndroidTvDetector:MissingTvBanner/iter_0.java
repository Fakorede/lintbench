package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

public class AndroidTvDetector extends ResourceXmlDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TAG_APPLICATION = "application";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_ACTIVITY_ALIAS = "activity-alias";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String TAG_CATEGORY = "category";
    private static final String ATTR_BANNER = "banner";
    private static final String ATTR_NAME = "name";
    private static final String VALUE_ANDROID_INTENT_ACTION_MAIN = "android.intent.action.MAIN";
    private static final String VALUE_ANDROID_INTENT_CATEGORY_LEANBACK_LAUNCHER =
            "android.intent.category.LEANBACK_LAUNCHER";

    public static final Issue ISSUE = Issue.create(
            "MissingTvBanner",
            "TV Missing Banner",
            "A TV application must provide a home screen banner for each localization if it "
                    + "includes a Leanback launcher intent filter. The banner is the app launch "
                    + "point that appears on the home screen in the apps and games rows. "
                    + "See https://developer.android.com/training/tv/start/start.html#banner",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    private boolean mHasLeanbackLauncher;
    private boolean mHasBanner;
    private Element mApplicationElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                TAG_APPLICATION,
                TAG_ACTIVITY,
                TAG_ACTIVITY_ALIAS,
                TAG_INTENT_FILTER
        );
    }

    @Override
    public void beforeCheckFile(Context context) {
        super.beforeCheckFile(context);
        mHasLeanbackLauncher = false;
        mHasBanner = false;
        mApplicationElement = null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();

        if (TAG_APPLICATION.equals(tag)) {
            mApplicationElement = element;
            if (hasBanner(element)) {
                mHasBanner = true;
            }
        } else if (TAG_ACTIVITY.equals(tag) || TAG_ACTIVITY_ALIAS.equals(tag)) {
            if (hasBanner(element)) {
                mHasBanner = true;
            }
        } else if (TAG_INTENT_FILTER.equals(tag)) {
            Element parent = (Element) element.getParentNode();
            if (parent != null
                    && (TAG_ACTIVITY.equals(parent.getTagName())
                    || TAG_ACTIVITY_ALIAS.equals(parent.getTagName()))
                    && isLeanbackLauncher(element)) {
                mHasLeanbackLauncher = true;
                if (hasBanner(parent)) {
                    mHasBanner = true;
                }
            }
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        super.afterCheckFile(context);
        if (mHasLeanbackLauncher && !mHasBanner && mApplicationElement != null) {
            context.report(
                    ISSUE,
                    mApplicationElement,
                    context.getLocation(mApplicationElement),
                    "Missing a banner for TV; add the android:banner attribute to the "
                            + "<application> or to the activity with the LEANBACK_LAUNCHER "
                            + "intent filter"
            );
        }
    }

    private static boolean hasBanner(Element element) {
        String banner = element.getAttributeNS(ANDROID_URI, ATTR_BANNER);
        return banner != null && !banner.isEmpty();
    }

    private static boolean isLeanbackLauncher(Element intentFilter) {
        boolean hasMain = false;
        boolean hasLeanback = false;

        for (int i = 0, n = intentFilter.getChildNodes().getLength(); i < n; i++) {
            org.w3c.dom.Node child = intentFilter.getChildNodes().item(i);
            if (child.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            String tag = childElement.getTagName();
            if (TAG_ACTION.equals(tag)) {
                String name = childElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if (VALUE_ANDROID_INTENT_ACTION_MAIN.equals(name)) {
                    hasMain = true;
                }
            } else if (TAG_CATEGORY.equals(tag)) {
                String name = childElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if (VALUE_ANDROID_INTENT_CATEGORY_LEANBACK_LAUNCHER.equals(name)) {
                    hasLeanback = true;
                }
            }
        }

        return hasMain && hasLeanback;
    }
}