package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_BANNER;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_CATEGORY;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;
import java.util.Arrays;
import java.util.Collection;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String LEANBACK_LAUNCHER_CATEGORY = "android.intent.category.LEANBACK_LAUNCHER";

    public static final Issue ISSUE =
            Issue.create(
                    "MissingTvBanner",
                    "TV Missing Banner",
                    "A TV application must provide a home screen banner for each localization "
                            + "if it includes a Leanback launcher intent filter. The banner is the "
                            + "app launch point that appears on the home screen in the apps and games rows.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private boolean hasLeanbackLauncher;
    private boolean hasBanner;
    private Location leanbackLocation;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_APPLICATION, TAG_ACTIVITY, TAG_CATEGORY);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        hasLeanbackLauncher = false;
        hasBanner = false;
        leanbackLocation = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (TAG_APPLICATION.equals(tag) || TAG_ACTIVITY.equals(tag)) {
            if (element.hasAttributeNS(ANDROID_URI, ATTR_BANNER)) {
                hasBanner = true;
            }
        } else if (TAG_CATEGORY.equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (LEANBACK_LAUNCHER_CATEGORY.equals(name)) {
                hasLeanbackLauncher = true;
                leanbackLocation = context.getLocation(element);
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (hasLeanbackLauncher && !hasBanner && leanbackLocation != null) {
            context.report(ISSUE, leanbackLocation,
                    "TV apps must provide a home screen banner if they include a Leanback launcher intent filter");
        }
    }
}