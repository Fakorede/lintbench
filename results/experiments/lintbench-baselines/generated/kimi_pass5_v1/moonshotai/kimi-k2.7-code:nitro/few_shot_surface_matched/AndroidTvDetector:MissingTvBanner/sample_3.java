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

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "MissingTvBanner",
                    "Missing TV Banner",
                    "A TV application must provide a home screen banner for each localization if"
                            + " it includes a Leanback launcher intent filter. The banner is the"
                            + " app launch point that appears on the home screen. Declare it with"
                            + " android:banner on the <application> element.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TAG_APPLICATION = "application";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String TAG_CATEGORY = "category";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_BANNER = "banner";
    private static final String ACTION_MAIN = "android.intent.action.MAIN";
    private static final String CATEGORY_LEANBACK_LAUNCHER =
            "android.intent.category.LEANBACK_LAUNCHER";

    private boolean mHasLeanbackLauncher;
    private boolean mHasApplicationBanner;
    private org.w3c.dom.Element mApplicationElement;

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Arrays.asList(TAG_APPLICATION, TAG_INTENT_FILTER);
    }

    @Override
    public void beforeCheckFile(Context context) {
        mHasLeanbackLauncher = false;
        mHasApplicationBanner = false;
        mApplicationElement = null;
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        String tag = element.getTagName();
        if (TAG_APPLICATION.equals(tag)) {
            mApplicationElement = element;
            String banner = element.getAttributeNS(ANDROID_URI, ATTR_BANNER);
            if (!banner.isEmpty()) {
                mHasApplicationBanner = true;
            }
        } else if (TAG_INTENT_FILTER.equals(tag)) {
            if (isLeanbackLauncher(element)) {
                mHasLeanbackLauncher = true;
            }
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        if (mHasLeanbackLauncher && !mHasApplicationBanner && mApplicationElement != null) {
            XmlContext xmlContext = (XmlContext) context;
            xmlContext.report(
                    ISSUE,
                    mApplicationElement,
                    xmlContext.getLocation(mApplicationElement),
                    "TV application missing a banner: declare android:banner on the"
                            + " <application> element.");
        }
    }

    private static boolean isLeanbackLauncher(org.w3c.dom.Element intentFilter) {
        boolean hasMain = false;
        boolean hasLeanback = false;
        for (org.w3c.dom.Node child = intentFilter.getFirstChild();
                child != null;
                child = child.getNextSibling()) {
            if (child.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                continue;
            }
            org.w3c.dom.Element childElement = (org.w3c.dom.Element) child;
            String tag = childElement.getTagName();
            if (TAG_ACTION.equals(tag)) {
                String name = childElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if (ACTION_MAIN.equals(name)) {
                    hasMain = true;
                }
            } else if (TAG_CATEGORY.equals(tag)) {
                String name = childElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if (CATEGORY_LEANBACK_LAUNCHER.equals(name)) {
                    hasLeanback = true;
                }
            }
        }
        return hasMain && hasLeanback;
    }
}