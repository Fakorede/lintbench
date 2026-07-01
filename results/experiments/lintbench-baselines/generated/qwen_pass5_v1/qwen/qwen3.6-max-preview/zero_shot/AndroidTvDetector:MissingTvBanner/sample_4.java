package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.jetbrains.annotations.NonNull;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingTvBanner",
            "TV Missing Banner",
            "A TV application must provide a home screen banner for each localization if it includes a Leanback launcher intent filter. " +
            "The banner is the app launch point that appears on the home screen in the apps and games rows.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final String LEANBACK_LAUNCHER_CATEGORY = "android.intent.category.LEANBACK_LAUNCHER";
    private static final String KEY_STATE = "AndroidTvDetector.FileState";

    private static class FileState {
        boolean appHasBanner = false;
        List<Element> leanbackActivities;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                SdkConstants.TAG_APPLICATION,
                SdkConstants.TAG_ACTIVITY,
                SdkConstants.TAG_ACTIVITY_ALIAS
        );
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        context.putClientData(KEY_STATE, new FileState());
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        FileState state = (FileState) context.getClientData(KEY_STATE);
        if (state == null) {
            return;
        }

        String tag = element.getTagName();
        if (SdkConstants.TAG_APPLICATION.equals(tag)) {
            state.appHasBanner = element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_BANNER);
        } else if (SdkConstants.TAG_ACTIVITY.equals(tag) || SdkConstants.TAG_ACTIVITY_ALIAS.equals(tag)) {
            if (hasLeanbackLauncherIntentFilter(element)) {
                if (state.leanbackActivities == null) {
                    state.leanbackActivities = new ArrayList<>();
                }
                state.leanbackActivities.add(element);
            }
        }
    }

    private static boolean hasLeanbackLauncherIntentFilter(@NonNull Element activity) {
        NodeList intentFilters = activity.getElementsByTagName(SdkConstants.TAG_INTENT_FILTER);
        for (int i = 0; i < intentFilters.getLength(); i++) {
            Element filter = (Element) intentFilters.item(i);
            NodeList categories = filter.getElementsByTagName(SdkConstants.TAG_CATEGORY);
            for (int j = 0; j < categories.getLength(); j++) {
                Element category = (Element) categories.item(j);
                String name = category.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                if (LEANBACK_LAUNCHER_CATEGORY.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        FileState state = (FileState) context.getClientData(KEY_STATE);
        if (state == null || state.leanbackActivities == null || state.leanbackActivities.isEmpty()) {
            return;
        }

        if (state.appHasBanner) {
            return;
        }

        for (Element activity : state.leanbackActivities) {
            if (!activity.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_BANNER)) {
                context.report(ISSUE, context.getLocation(activity),
                        "TV applications must provide a home screen banner for each localization if they include a Leanback launcher intent filter. " +
                        "Add `android:banner` to the `<application>` or `<activity>` tag.");
            }
        }
    }
}