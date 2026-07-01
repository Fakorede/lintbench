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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "MissingTvBanner",
                    "TV Missing Banner",
                    "A TV application must provide a home screen banner for each localization if it includes a Leanback launcher intent filter. The banner is the app launch point that appears on the home screen in the apps and games rows.",
                    Category.CORRECTNESS,
                    7,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean applicationHasBanner;
    private final List<Element> leanbackActivities = new ArrayList<>();
    private final Map<Element, Boolean> activityHasBanner = new HashMap<>();

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("application", "activity", "activity-alias", "category");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        applicationHasBanner = false;
        leanbackActivities.clear();
        activityHasBanner.clear();
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (leanbackActivities.isEmpty()) {
            return;
        }
        if (applicationHasBanner) {
            return;
        }

        XmlContext xmlContext = (XmlContext) context;
        for (Element activity : leanbackActivities) {
            Boolean hasBanner = activityHasBanner.get(activity);
            if (hasBanner == null || !hasBanner) {
                xmlContext.report(
                        ISSUE,
                        xmlContext.getLocation(activity),
                        "TV apps must provide a home screen banner for each localization if they include a Leanback launcher intent filter.");
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if ("application".equals(tag)) {
            applicationHasBanner = hasBanner(element);
        } else if ("activity".equals(tag) || "activity-alias".equals(tag)) {
            activityHasBanner.put(element, hasBanner(element));
        } else if ("category".equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, "name");
            if (LEANBACK_LAUNCHER.equals(name)) {
                Element parent = findAncestor(element, "activity");
                if (parent == null) {
                    parent = findAncestor(element, "activity-alias");
                }
                if (parent != null && !leanbackActivities.contains(parent)) {
                    leanbackActivities.add(parent);
                }
            }
        }
    }

    private boolean hasBanner(@NonNull Element element) {
        String banner = element.getAttributeNS(ANDROID_URI, "banner");
        return banner != null && !banner.isEmpty();
    }

    private Element findAncestor(@NonNull Element element, @NonNull String tagName) {
        Node current = element.getParentNode();
        while (current != null) {
            if (current instanceof Element) {
                Element el = (Element) current;
                if (tagName.equals(el.getTagName())) {
                    return el;
                }
            }
            current = current.getParentNode();
        }
        return null;
    }
}