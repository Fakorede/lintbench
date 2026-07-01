package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
        "MissingLeanbackLauncher",
        "Missing Leanback Launcher Intent Filter",
        "An application intended to run on TV devices must declare a launcher activity for TV in its manifest using a `android.intent.category.LEANBACK_LAUNCHER` intent filter.\n\n" +
        "Reference: https://developer.android.com/training/tv/start/start.html#tv-activity",
        Category.CORRECTNESS,
        6,
        Severity.WARNING,
        new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("manifest");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (hasLeanbackLauncherIntentFilter(element)) {
            return;
        }

        context.report(
            ISSUE,
            element,
            context.getLocation(element),
            "The manifest should contain an activity with an intent filter for `android.intent.category.LEANBACK_LAUNCHER` for TV support."
        );
    }

    private boolean hasLeanbackLauncherIntentFilter(Element manifest) {
        NodeList activities = manifest.getElementsByTagName("activity");
        for (int i = 0; i < activities.getLength(); i++) {
            Element activity = (Element) activities.item(i);
            if (activityHasLeanbackCategory(activity)) {
                return true;
            }
        }

        NodeList aliases = manifest.getElementsByTagName("activity-alias");
        for (int i = 0; i < aliases.getLength(); i++) {
            Element alias = (Element) aliases.item(i);
            if (activityHasLeanbackCategory(alias)) {
                return true;
            }
        }

        return false;
    }

    private boolean activityHasLeanbackCategory(Element component) {
        NodeList intentFilters = component.getElementsByTagName("intent-filter");
        for (int i = 0; i < intentFilters.getLength(); i++) {
            Element intentFilter = (Element) intentFilters.item(i);
            NodeList categories = intentFilter.getElementsByTagName("category");
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