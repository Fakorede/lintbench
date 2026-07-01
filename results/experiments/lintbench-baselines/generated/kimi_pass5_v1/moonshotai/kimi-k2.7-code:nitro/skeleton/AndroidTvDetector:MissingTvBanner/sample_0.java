package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "MissingTvBanner",
                    "TV Missing Banner",
                    "TV applications that include a Leanback launcher intent filter must provide "
                            + "a home screen banner via the android:banner attribute on the "
                            + "<application> element. This banner is the app launch point that "
                            + "appears on the home screen in the apps and games rows.",
                    Category.CORRECTNESS,
                    7,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mHasLeanbackLauncher;
    private org.w3c.dom.Element mApplicationElement;
    private boolean mHasBanner;

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Arrays.asList("application", "category");
    }

    @Override
    public void beforeCheckFile(Context context) {
        mHasLeanbackLauncher = false;
        mApplicationElement = null;
        mHasBanner = false;
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        String tag = element.getTagName();
        if ("application".equals(tag)) {
            mApplicationElement = element;
            String banner = element.getAttributeNS(ANDROID_URI, "banner");
            mHasBanner = banner != null && !banner.isEmpty();
        } else if ("category".equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, "name");
            if (LEANBACK_LAUNCHER.equals(name)) {
                mHasLeanbackLauncher = true;
            }
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        if (mHasLeanbackLauncher && mApplicationElement != null && !mHasBanner) {
            XmlContext xmlContext = (XmlContext) context;
            xmlContext.report(
                    ISSUE,
                    mApplicationElement,
                    xmlContext.getLocation(mApplicationElement),
                    "TV applications using the Leanback launcher must specify an "
                            + "android:banner on the <application> element");
        }
    }
}