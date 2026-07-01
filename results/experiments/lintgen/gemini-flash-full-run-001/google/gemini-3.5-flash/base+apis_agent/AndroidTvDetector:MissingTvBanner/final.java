package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingTvBanner",
            "TV Missing Banner",
            "A TV application must provide a home screen banner for each localization " +
            "if it includes a Leanback launcher intent filter. The banner is the app " +
            "launch point that appears on the home screen in the apps and games rows.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(
                    AndroidTvDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    );

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        Element application = null;
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element) {
                Element child = (Element) children.item(i);
                if ("application".equals(child.getTagName())) {
                    application = child;
                    break;
                }
            }
        }

        if (application == null) {
            return;
        }

        boolean hasAppBanner = application.hasAttributeNS(SdkConstants.ANDROID_URI, "banner");
        NodeList activities = application.getElementsByTagName("activity");
        NodeList activityAliases = application.getElementsByTagName("activity-alias");

        List<Element> launcherActivitiesWithoutBanner = new ArrayList<>();

        checkActivities(activities, hasAppBanner, launcherActivitiesWithoutBanner);
        checkActivities(activityAliases, hasAppBanner, launcherActivitiesWithoutBanner);

        if (!launcherActivitiesWithoutBanner.isEmpty()) {
            for (Element activity : launcherActivitiesWithoutBanner) {
                context.report(
                        ISSUE,
                        context.getNameLocation(activity),
                        "Expects a banner attribute (`android:banner`) in the template or application " +
                        "since it supports Leanback launcher."
                );
            }
        }
    }

    private void checkActivities(NodeList activities, boolean hasAppBanner, List<Element> launcherActivitiesWithoutBanner) {
        for (int i = 0; i < activities.getLength(); i++) {
            Element activity = (Element) activities.item(i);
            if (hasLeanbackLauncher(activity)) {
                boolean hasActivityBanner = activity.hasAttributeNS(SdkConstants.ANDROID_URI, "banner");
                if (!hasAppBanner && !hasActivityBanner) {
                    launcherActivitiesWithoutBanner.add(activity);
                }
            }
        }
    }

    private boolean hasLeanbackLauncher(Element activity) {
        NodeList intentFilters = activity.getElementsByTagName("intent-filter");
        for (int i = 0; i < intentFilters.getLength(); i++) {
            Element filter = (Element) intentFilters.item(i);
            NodeList categories = filter.getElementsByTagName("category");
            for (int j = 0; j < categories.getLength(); j++) {
                Element category = (Element) categories.item(j);
                String name = category.getAttributeNS(SdkConstants.ANDROID_URI, "name");
                if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }
}