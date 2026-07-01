package com.android.tools.lint.checks;

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
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "MissingTvBanner",
                    "TV Missing Banner",
                    "A TV application must provide a home screen banner for each "
                    + "localization if it includes a Leanback launcher intent filter. "
                    + "The banner is the app launch point that appears on the home "
                    + "screen in the apps and games rows.",
                    Category.CORRECTNESS,
                    7,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean hasLeanbackLauncher;
    private boolean hasBanner;
    private Location applicationLocation;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("application", "intent-filter");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        hasLeanbackLauncher = false;
        hasBanner = false;
        applicationLocation = null;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (hasLeanbackLauncher && !hasBanner && applicationLocation != null) {
            context.report(ISSUE, applicationLocation,
                    "TV applications must provide a home screen banner if they include a Leanback launcher intent filter");
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if ("application".equals(tag)) {
            applicationLocation = context.getLocation(element);
            hasBanner = element.hasAttributeNS(ANDROID_URI, "banner");
        } else if ("intent-filter".equals(tag)) {
            if (isLeanbackLauncher(element)) {
                hasLeanbackLauncher = true;
            }
        }
    }

    private boolean isLeanbackLauncher(Element intentFilter) {
        boolean hasMainAction = false;
        boolean hasLeanbackCategory = false;
        NodeList children = intentFilter.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element e = (Element) child;
                String name = e.getAttributeNS(ANDROID_URI, "name");
                if ("action".equals(e.getTagName()) && "android.intent.action.MAIN".equals(name)) {
                    hasMainAction = true;
                } else if ("category".equals(e.getTagName()) && "android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                    hasLeanbackCategory = true;
                }
            }
        }
        return hasMainAction && hasLeanbackCategory;
    }
}