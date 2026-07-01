package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_INTENT_CATEGORY_LEANBACK_LAUNCHER;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_BANNER;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_ACTIVITY_ALIAS;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_CATEGORY;
import static com.android.SdkConstants.TAG_INTENT_FILTER;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
                    "MissingTvBanner",
                    "TV Missing Banner",
                    "A TV application must provide a home screen banner for each localization if it includes a Leanback launcher intent filter. "
                            + "The banner is the app launch point that appears on the home screen in the apps and games rows.\n"
                            + "You should declare the banner using the `android:banner` attribute on the `<application>` tag, "
                            + "or on the specific activity that declares the Leanback launcher intent filter.",
                    Category.USABILITY,
                    5,
                    Severity.WARNING,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE))
            .addMoreInfo("https://developer.android.com/training/tv/start/start.html#banner");

    @Override
    public @NotNull Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_ACTIVITY, TAG_ACTIVITY_ALIAS);
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        if (element.hasAttributeNS(ANDROID_URI, ATTR_BANNER)) {
            return;
        }

        if (!hasLeanbackLauncherFilter(element)) {
            return;
        }

        Element application = getApplicationElement(element);
        if (application != null && application.hasAttributeNS(ANDROID_URI, ATTR_BANNER)) {
            return;
        }

        context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing TV banner: this Leanback launcher activity should declare android:banner, "
                        + "or the application should declare a default android:banner.");
    }

    private static boolean hasLeanbackLauncherFilter(@NotNull Element activity) {
        NodeList intentFilters = activity.getElementsByTagName(TAG_INTENT_FILTER);
        for (int i = 0; i < intentFilters.getLength(); i++) {
            Element filter = (Element) intentFilters.item(i);
            NodeList categories = filter.getElementsByTagName(TAG_CATEGORY);
            for (int j = 0; j < categories.getLength(); j++) {
                Element category = (Element) categories.item(j);
                String name = category.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if (ANDROID_INTENT_CATEGORY_LEANBACK_LAUNCHER.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Element getApplicationElement(@NotNull Element element) {
        NodeList applications = element.getOwnerDocument().getElementsByTagName(TAG_APPLICATION);
        if (applications.getLength() > 0) {
            return (Element) applications.item(0);
        }
        return null;
    }
}