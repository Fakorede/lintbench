package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ACTION_MAIN = "android.intent.action.MAIN";
    private static final String CATEGORY_LEANBACK_LAUNCHER =
            "android.intent.category.LEANBACK_LAUNCHER";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "MissingTvBanner",
                    "TV Missing Banner",
                    "A TV application that declares a Leanback launcher intent filter must "
                            + "provide a home screen banner for each localization by setting the "
                            + "`android:banner` attribute on the `<application>` element. The "
                            + "banner is the app launch point that appears on the home screen in "
                            + "the apps and games rows.",
                    Category.CORRECTNESS,
                    7,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mIsManifest;
    private boolean mHasLeanbackLauncher;
    private boolean mHasBanner;
    private org.w3c.dom.Element mApplicationElement;

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList("application");
    }

    @Override
    public void beforeCheckFile(Context context) {
        mIsManifest = "AndroidManifest.xml".equals(context.file.getName());
        mHasLeanbackLauncher = false;
        mHasBanner = false;
        mApplicationElement = null;
    }

    @Override
    public void afterCheckFile(Context context) {
        if (!mIsManifest
                || !mHasLeanbackLauncher
                || mHasBanner
                || mApplicationElement == null) {
            return;
        }

        XmlContext xmlContext = (XmlContext) context;
        xmlContext.report(
                ISSUE,
                mApplicationElement,
                xmlContext.getElementLocation(mApplicationElement),
                "The application is missing an Android TV banner (`android:banner`) required by "
                        + "the Leanback launcher intent filter.");
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        if (!mIsManifest) {
            return;
        }

        if ("application".equals(element.getTagName())) {
            mApplicationElement = element;
            mHasBanner =
                    element.hasAttribute("android:banner")
                            || element.hasAttributeNS(ANDROID_URI, "banner");
            mHasLeanbackLauncher = hasLeanbackLauncher(element);
        }
    }

    private boolean hasLeanbackLauncher(org.w3c.dom.Element application) {
        org.w3c.dom.NodeList filters = application.getElementsByTagName("intent-filter");
        for (int i = 0; i < filters.getLength(); i++) {
            org.w3c.dom.Element filter = (org.w3c.dom.Element) filters.item(i);

            boolean hasMain = false;
            boolean hasLeanback = false;

            org.w3c.dom.NodeList children = filter.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                org.w3c.dom.Node child = children.item(j);
                if (child.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                    continue;
                }

                org.w3c.dom.Element childElement = (org.w3c.dom.Element) child;
                String tag = childElement.getTagName();

                if ("action".equals(tag)
                        && ACTION_MAIN.equals(childElement.getAttributeNS(ANDROID_URI, "name"))) {
                    hasMain = true;
                } else if ("category".equals(tag)
                        && CATEGORY_LEANBACK_LAUNCHER.equals(
                                childElement.getAttributeNS(ANDROID_URI, "name"))) {
                    hasLeanback = true;
                }

                if (hasMain && hasLeanback) {
                    return true;
                }
            }
        }
        return false;
    }
}