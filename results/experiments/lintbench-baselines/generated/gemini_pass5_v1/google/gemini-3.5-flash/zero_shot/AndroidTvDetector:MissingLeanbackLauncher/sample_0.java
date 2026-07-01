package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingLeanbackLauncher",
            "Missing Leanback Launcher Intent Filter",
            "An application intended to run on TV devices must declare a launcher activity " +
            "for TV in its manifest using a `android.intent.category.LEANBACK_LAUNCHER` intent filter.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(
                    AndroidTvDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    );

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        boolean hasLeanbackFeature = false;
        boolean hasTvHardwareFeature = false;

        NodeList usesFeatures = root.getElementsByTagName("uses-feature");
        for (int i = 0; i < usesFeatures.getLength(); i++) {
            Element feature = (Element) usesFeatures.item(i);
            String name = feature.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if ("android.software.leanback".equals(name)) {
                hasLeanbackFeature = true;
            } else if ("android.hardware.type.television".equals(name)) {
                hasTvHardwareFeature = true;
            }
        }

        if (!hasLeanbackFeature && !hasTvHardwareFeature) {
            return;
        }

        boolean hasLeanbackLauncher = false;
        NodeList activities = root.getElementsByTagName("activity");
        if (hasLeanbackLauncherCategory(activities)) {
            hasLeanbackLauncher = true;
        } else {
            NodeList activityAliases = root.getElementsByTagName("activity-alias");
            if (hasLeanbackLauncherCategory(activityAliases)) {
                hasLeanbackLauncher = true;
            }
        }

        if (!hasLeanbackLauncher) {
            context.report(
                    ISSUE,
                    root,
                    context.getLocation(root),
                    "An application intended to run on TV devices must declare a launcher activity for TV"
            );
        }
    }

    private boolean hasLeanbackLauncherCategory(NodeList activities) {
        for (int i = 0; i < activities.getLength(); i++) {
            Element activity = (Element) activities.item(i);
            NodeList intentFilters = activity.getElementsByTagName("intent-filter");
            for (int j = 0; j < intentFilters.getLength(); j++) {
                Element filter = (Element) intentFilters.item(j);
                NodeList categories = filter.getElementsByTagName("category");
                for (int k = 0; k < categories.getLength(); k++) {
                    Element category = (Element) categories.item(k);
                    String name = category.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                    if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}