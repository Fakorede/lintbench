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
import org.w3c.dom.Element;
import java.util.Arrays;
import java.util.Collection;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String LEANBACK_LAUNCHER_CATEGORY = "android.intent.category.LEANBACK_LAUNCHER";

    public static final Issue ISSUE = Issue.create(
            "MissingTvBanner",
            "TV Missing Banner",
            "A TV application must provide a home screen banner for each localization if it " +
                    "includes a Leanback launcher intent filter. The banner is the app launch " +
                    "point that appears on the home screen in the apps and games rows.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private boolean hasLeanbackLauncher;
    private boolean hasBanner;
    private Element applicationElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("application", "category");
    }

    @Override
    public void beforeCheckFile(Context context) {
        hasLeanbackLauncher = false;
        hasBanner = false;
        applicationElement = null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        if ("application".equals(tag)) {
            applicationElement = element;
            String banner = element.getAttributeNS(ANDROID_URI, "banner");
            if (banner != null && !banner.isEmpty()) {
                hasBanner = true;
            }
        } else if ("category".equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, "name");
            if (LEANBACK_LAUNCHER_CATEGORY.equals(name)) {
                hasLeanbackLauncher = true;
            }
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        if (hasLeanbackLauncher && !hasBanner && applicationElement != null) {
            XmlContext xmlContext = (XmlContext) context;
            xmlContext.report(ISSUE, applicationElement, xmlContext.getLocation(applicationElement),
                    "TV applications must provide a home screen banner when including a Leanback launcher intent filter");
        }
    }
}