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
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "MissingTvBanner",
                    "TV Missing Banner",
                    "A TV application must provide a home screen banner for each " +
                    "localization if it includes a Leanback launcher intent filter. " +
                    "The banner is the app launch point that appears on the home " +
                    "screen in the apps and games rows.",
                    Category.CORRECTNESS,
                    7,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("category");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        // No-op
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        // No-op
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
        if (name.isEmpty()) {
            name = element.getAttribute("android:name");
        }

        if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
            Node intentFilter = element.getParentNode();
            if (intentFilter instanceof Element && "intent-filter".equals(intentFilter.getNodeName())) {
                Node activity = intentFilter.getParentNode();
                if (activity instanceof Element && ("activity".equals(activity.getNodeName()) || "activity-alias".equals(activity.getNodeName()))) {
                    Element activityElement = (Element) activity;
                    if (hasBanner(activityElement)) {
                        return;
                    }

                    Node application = activityElement.getParentNode();
                    if (application instanceof Element && "application".equals(application.getNodeName())) {
                        Element applicationElement = (Element) application;
                        if (hasBanner(applicationElement)) {
                            return;
                        }
                    }

                    context.report(
                            ISSUE,
                            activityElement,
                            context.getLocation(activityElement),
                            "The TV activity or its application must define a banner image (`android:banner`) if it supports Leanback launcher"
                    );
                }
            }
        }
    }

    private boolean hasBanner(Element element) {
        String banner = element.getAttributeNS("http://schemas.android.com/apk/res/android", "banner");
        if (banner == null || banner.isEmpty()) {
            banner = element.getAttribute("android:banner");
        }
        return banner != null && !banner.isEmpty();
    }
}