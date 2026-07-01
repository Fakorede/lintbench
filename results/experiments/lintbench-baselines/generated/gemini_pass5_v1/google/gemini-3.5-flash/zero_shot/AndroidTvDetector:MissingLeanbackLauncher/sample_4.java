package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
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
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingLeanbackLauncher",
            "Missing Leanback Launcher Intent Filter",
            "An application intended to run on TV devices must declare a launcher " +
            "activity for TV in its manifest using a `android.intent.category.LEANBACK_LAUNCHER` " +
            "intent filter.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(
                    AndroidTvDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_MANIFEST);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Element targetElement = null;
        NodeList children = element.getElementsByTagName(SdkConstants.TAG_USES_FEATURE);
        for (int i = 0; i < children.getLength(); i++) {
            Element child = (Element) children.item(i);
            String name = child.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if ("android.software.leanback".equals(name) || "android.hardware.type.television".equals(name)) {
                targetElement = child;
                break;
            }
        }

        if (targetElement != null && !hasLeanbackLauncher(element)) {
            context.report(
                    ISSUE,
                    targetElement,
                    context.getLocation(targetElement),
                    "An application intended to run on TV devices must declare a launcher activity for TV in its manifest using a `android.intent.category.LEANBACK_LAUNCHER` intent filter."
            );
        }
    }

    private static boolean hasLeanbackLauncher(Element manifest) {
        NodeList activities = manifest.getElementsByTagName(SdkConstants.TAG_ACTIVITY);
        if (checkLauncher(activities)) {
            return true;
        }
        NodeList activityAliases = manifest.getElementsByTagName(SdkConstants.TAG_ACTIVITY_ALIAS);
        if (checkLauncher(activityAliases)) {
            return true;
        }
        return false;
    }

    private static boolean checkLauncher(NodeList activities) {
        for (int i = 0; i < activities.getLength(); i++) {
            Element activity = (Element) activities.item(i);
            NodeList intentFilters = activity.getElementsByTagName(SdkConstants.TAG_INTENT_FILTER);
            for (int j = 0; j < intentFilters.getLength(); j++) {
                Element filter = (Element) intentFilters.item(j);
                if (hasLeanbackLauncherFilter(filter)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasLeanbackLauncherFilter(Element filter) {
        boolean hasMain = false;
        boolean hasLeanback = false;
        NodeList actions = filter.getElementsByTagName(SdkConstants.TAG_ACTION);
        for (int i = 0; i < actions.getLength(); i++) {
            Element action = (Element) actions.item(i);
            String name = action.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if (SdkConstants.ACTION_MAIN.equals(name)) {
                hasMain = true;
            }
        }
        NodeList categories = filter.getElementsByTagName(SdkConstants.TAG_CATEGORY);
        for (int i = 0; i < categories.getLength(); i++) {
            Element category = (Element) categories.item(i);
            String name = category.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                hasLeanback = true;
            }
        }
        return hasMain && hasLeanback;
    }
}