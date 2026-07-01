package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    private static final String LEANBACK_LAUNCHER =
            "android.intent.category.LEANBACK_LAUNCHER";

    public static final Issue ISSUE = Issue.create(
            "MissingTvBanner",
            "TV Missing Banner",
            "A TV application that includes a Leanback launcher intent filter must " +
            "provide an application banner. The banner is the launch point that " +
            "appears on the home screen in the apps and games rows.",
            "https://developer.android.com/training/tv/start/start.html#banner",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    @Override
    public @NotNull Collection<String> getApplicableElements() {
        return Arrays.asList(SdkConstants.TAG_APPLICATION);
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        if (!hasLeanbackLauncherFilter(element)) {
            return;
        }

        if (!element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_BANNER)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing TV banner: declare android:banner on the application element " +
                    "when providing a Leanback launcher intent filter.");
        }
    }

    private static boolean hasLeanbackLauncherFilter(@NotNull Element application) {
        NodeList activities = application.getElementsByTagName(SdkConstants.TAG_ACTIVITY);
        for (int i = 0; i < activities.getLength(); i++) {
            Element activity = (Element) activities.item(i);
            if (hasCategory(activity, LEANBACK_LAUNCHER)) {
                return true;
            }
        }

        NodeList aliases = application.getElementsByTagName(SdkConstants.TAG_ACTIVITY_ALIAS);
        for (int i = 0; i < aliases.getLength(); i++) {
            Element alias = (Element) aliases.item(i);
            if (hasCategory(alias, LEANBACK_LAUNCHER)) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasCategory(@NotNull Element parent, @NotNull String categoryName) {
        NodeList filters = parent.getElementsByTagName(SdkConstants.TAG_INTENT_FILTER);
        for (int i = 0; i < filters.getLength(); i++) {
            Element filter = (Element) filters.item(i);
            NodeList categories = filter.getElementsByTagName(SdkConstants.TAG_CATEGORY);
            for (int j = 0; j < categories.getLength(); j++) {
                Element category = (Element) categories.item(j);
                String name = category.getAttributeNS(SdkConstants.ANDROID_URI,
                        SdkConstants.ATTR_NAME);
                if (categoryName.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }
}