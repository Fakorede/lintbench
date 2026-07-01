package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.*;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class AndroidTvDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "MissingTvBanner",
            "TV Missing Banner",
            "A TV application must provide a home screen banner for each localization if it includes a Leanback launcher intent filter. " +
            "The banner is the app launch point that appears on the home screen in the apps and games rows.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final String LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER";
    private static final String ATTR_BANNER = "banner";
    private static final String TAG_APPLICATION = "application";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_CATEGORY = "category";
    private static final String ATTR_NAME = "name";

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MANIFEST;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("manifest");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Element application = getFirstSubTag(element, TAG_APPLICATION);
        boolean appHasBanner = application != null && hasBannerAttribute(application);

        NodeList activities = element.getElementsByTagName(TAG_ACTIVITY);
        for (int i = 0; i < activities.getLength(); i++) {
            Element activity = (Element) activities.item(i);
            if (hasLeanbackLauncher(activity)) {
                if (!hasBannerAttribute(activity) && !appHasBanner) {
                    context.report(ISSUE, activity, context.getLocation(activity),
                            "Expecting `android:banner` attribute for TV launcher activity");
                }
            }
        }
    }

    private static boolean hasLeanbackLauncher(@NonNull Element activity) {
        NodeList intentFilters = activity.getElementsByTagName(TAG_INTENT_FILTER);
        for (int i = 0; i < intentFilters.getLength(); i++) {
            Element intentFilter = (Element) intentFilters.item(i);
            NodeList categories = intentFilter.getElementsByTagName(TAG_CATEGORY);
            for (int j = 0; j < categories.getLength(); j++) {
                Element category = (Element) categories.item(j);
                String name = category.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if (LEANBACK_LAUNCHER.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasBannerAttribute(@NonNull Element element) {
        String banner = element.getAttributeNS(ANDROID_URI, ATTR_BANNER);
        return banner != null && !banner.isEmpty();
    }

    @Nullable
    private static Element getFirstSubTag(@NonNull Element parent, @NonNull String tagName) {
        NodeList list = parent.getElementsByTagName(tagName);
        return list.getLength() > 0 ? (Element) list.item(0) : null;
    }
}