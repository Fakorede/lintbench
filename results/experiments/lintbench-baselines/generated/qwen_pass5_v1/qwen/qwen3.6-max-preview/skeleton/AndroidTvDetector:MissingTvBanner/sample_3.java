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
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "MissingTvBanner",
                    "TV Missing Banner",
                    "A TV application must provide a home screen banner for each localization if it includes a Leanback launcher intent filter. The banner is the app launch point that appears on the home screen in the apps and games rows.",
                    Category.CORRECTNESS,
                    7,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER";

    private boolean isManifest;
    private boolean hasLeanbackLauncher;
    private boolean hasBanner;
    private Element applicationElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("application", "activity", "activity-alias", "category");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        isManifest = context.file.getName().equals("AndroidManifest.xml");
        if (!isManifest) {
            return;
        }
        hasLeanbackLauncher = false;
        hasBanner = false;
        applicationElement = null;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!isManifest) {
            return;
        }
        if (hasLeanbackLauncher && !hasBanner) {
            Element locationElement = applicationElement != null ? applicationElement : ((XmlContext) context).document.getDocumentElement();
            String message = "TV apps must provide a home screen banner for each localization if they include a Leanback launcher intent filter. "
                    + "Add android:banner=\"@drawable/...\" to the <application> or <activity> tag.";
            context.report(ISSUE, locationElement, ((XmlContext) context).getLocation(locationElement), message);
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!isManifest) {
            return;
        }
        String tag = element.getTagName();
        if (tag.equals("application")) {
            applicationElement = element;
            if (element.hasAttributeNS(ANDROID_URI, "banner")) {
                hasBanner = true;
            }
        } else if (tag.equals("activity") || tag.equals("activity-alias")) {
            if (element.hasAttributeNS(ANDROID_URI, "banner")) {
                hasBanner = true;
            }
        } else if (tag.equals("category")) {
            String name = element.getAttributeNS(ANDROID_URI, "name");
            if (LEANBACK_LAUNCHER.equals(name)) {
                hasLeanbackLauncher = true;
            }
        }
    }
}