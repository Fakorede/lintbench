package com.android.tools.lint.checks;

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

import java.util.Arrays;
import java.util.Collection;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "MissingTvBanner",
                    "TV Missing Banner",
                    "A TV application must provide a home screen banner for each localization if it includes a Leanback launcher intent filter. The banner is the app launch point that appears on the home screen in the apps and games rows.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final String CATEGORY_LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER";
    private static final String ATTR_BANNER = "banner";
    private static final String NODE_APPLICATION = "application";
    private static final String NODE_ACTIVITY = "activity";
    private static final String NODE_CATEGORY = "category";

    private boolean hasLeanbackLauncher;
    private boolean hasAppBanner;
    private boolean hasActivityBanner;
    private Element applicationElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(NODE_APPLICATION, NODE_ACTIVITY, NODE_CATEGORY);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        hasLeanbackLauncher = false;
        hasAppBanner = false;
        hasActivityBanner = false;
        applicationElement = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (NODE_APPLICATION.equals(tag)) {
            applicationElement = element;
            if (hasBanner(element)) {
                hasAppBanner = true;
            }
        } else if (NODE_ACTIVITY.equals(tag)) {
            if (hasBanner(element)) {
                hasActivityBanner = true;
            }
        } else if (NODE_CATEGORY.equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (CATEGORY_LEANBACK_LAUNCHER.equals(name)) {
                hasLeanbackLauncher = true;
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (hasLeanbackLauncher && !hasAppBanner && !hasActivityBanner) {
            XmlContext xmlContext = (XmlContext) context;
            Element reportNode = applicationElement != null
                    ? applicationElement
                    : xmlContext.document.getDocumentElement();
            xmlContext.report(
                    ISSUE,
                    reportNode,
                    xmlContext.getLocation(reportNode),
                    "TV apps must provide a home screen banner if they include a Leanback launcher intent filter. "
                            + "Add an `android:banner` attribute to the <application> or <activity> tag.");
        }
    }

    private static boolean hasBanner(@NonNull Element element) {
        return element.hasAttributeNS(ANDROID_URI, ATTR_BANNER);
    }
}