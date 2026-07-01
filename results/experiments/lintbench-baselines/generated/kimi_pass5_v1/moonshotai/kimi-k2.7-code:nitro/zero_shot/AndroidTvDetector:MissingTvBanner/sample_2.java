package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    private static final String LEANBACK_LAUNCHER =
            "android.intent.category.LEANBACK_LAUNCHER";
    private static final String ACTION_MAIN = "android.intent.action.MAIN";

    public static final Issue ISSUE = Issue.create(
            "MissingTvBanner",
            "Missing TV Banner",
            "A TV application that includes a Leanback launcher intent filter must provide "
                    + "a home screen banner for each localization. The banner is the app launch "
                    + "point that appears on the home screen in the apps and games rows. Add "
                    + "`android:banner` to the `<application>` element.\n"
                    + "\n"
                    + "Reference: https://developer.android.com/training/tv/start/start.html#banner",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(SdkConstants.TAG_APPLICATION);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!SdkConstants.TAG_APPLICATION.equals(element.getTagName())) {
            return;
        }

        if (element.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_BANNER)) {
            return;
        }

        if (hasLeanbackLauncherActivity(element)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing TV banner: the application declares a Leanback launcher intent "
                            + "filter but does not declare an `android:banner` attribute.");
        }
    }

    private static boolean hasLeanbackLauncherActivity(Element application) {
        if (hasLeanbackLauncher(application, SdkConstants.TAG_ACTIVITY)) {
            return true;
        }
        return hasLeanbackLauncher(application, SdkConstants.TAG_ACTIVITY_ALIAS);
    }

    private static boolean hasLeanbackLauncher(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        for (int i = 0; i < nodes.getLength(); i++) {
            Element node = (Element) nodes.item(i);
            if (isLeanbackLauncher(node)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLeanbackLauncher(Element activity) {
        NodeList filters = activity.getElementsByTagName(SdkConstants.TAG_INTENT_FILTER);
        for (int i = 0; i < filters.getLength(); i++) {
            Element filter = (Element) filters.item(i);
            if (hasAction(filter, ACTION_MAIN) && hasCategory(filter, LEANBACK_LAUNCHER)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAction(Element filter, String actionName) {
        return hasChildWithName(filter, SdkConstants.TAG_ACTION, actionName);
    }

    private static boolean hasCategory(Element filter, String categoryName) {
        return hasChildWithName(filter, SdkConstants.TAG_CATEGORY, categoryName);
    }

    private static boolean hasChildWithName(Element parent, String tag, String value) {
        NodeList children = parent.getElementsByTagName(tag);
        for (int i = 0; i < children.getLength(); i++) {
            Element child = (Element) children.item(i);
            String name = child.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if (value.equals(name)) {
                return true;
            }
        }
        return false;
    }
}