package com.android.tools.lint.checks;

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

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final String TAG_APPLICATION = "application";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_ACTIVITY_ALIAS = "activity-alias";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String TAG_CATEGORY = "category";

    private static final String ATTR_NAME = "name";
    private static final String ATTR_BANNER = "banner";

    private static final String ACTION_MAIN = "android.intent.action.MAIN";
    private static final String CATEGORY_LEANBACK_LAUNCHER =
            "android.intent.category.LEANBACK_LAUNCHER";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "MissingTvBanner",
                    "TV Missing Banner",
                    "A TV application must provide a home screen banner for each localization if it includes a Leanback launcher intent filter. "
                            + "The banner is the app launch point that appears on the home screen in the apps and games rows. "
                            + "Declare the banner on the `<application>` element using the `android:banner` attribute.",
                    Category.CORRECTNESS,
                    7,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mHasLeanbackLauncher;
    private boolean mHasBanner;
    private boolean mMainAction;
    private boolean mLeanbackCategory;
    private Element mApplicationElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                TAG_APPLICATION,
                TAG_ACTIVITY,
                TAG_ACTIVITY_ALIAS,
                TAG_INTENT_FILTER,
                TAG_ACTION,
                TAG_CATEGORY);
    }

    @Override
    public void beforeCheckFile(Context context) {
        mHasLeanbackLauncher = false;
        mHasBanner = false;
        mMainAction = false;
        mLeanbackCategory = false;
        mApplicationElement = null;
    }

    @Override
    public void afterCheckFile(Context context) {
        if (mHasLeanbackLauncher && !mHasBanner && mApplicationElement != null) {
            XmlContext xmlContext = (XmlContext) context;
            xmlContext.report(
                    ISSUE,
                    mApplicationElement,
                    xmlContext.getLocation(mApplicationElement),
                    "TV applications that include a Leanback launcher intent filter must specify the `android:banner` attribute on the `<application>` element.");
        }
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tagName = element.getTagName();
        if (TAG_APPLICATION.equals(tagName)) {
            mApplicationElement = element;
            mHasBanner = element.hasAttributeNS(ANDROID_URI, ATTR_BANNER);
        } else if (TAG_INTENT_FILTER.equals(tagName)) {
            mMainAction = false;
            mLeanbackCategory = false;
        } else if (TAG_ACTION.equals(tagName)
                && ACTION_MAIN.equals(element.getAttributeNS(ANDROID_URI, ATTR_NAME))) {
            mMainAction = true;
            if (mLeanbackCategory) {
                mHasLeanbackLauncher = true;
            }
        } else if (TAG_CATEGORY.equals(tagName)
                && CATEGORY_LEANBACK_LAUNCHER.equals(
                        element.getAttributeNS(ANDROID_URI, ATTR_NAME))) {
            mLeanbackCategory = true;
            if (mMainAction) {
                mHasLeanbackLauncher = true;
            }
        }
    }
}