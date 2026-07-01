package com.android.tools.lint.checks;

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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    public static final Issue MISSING_TV_BANNER = Issue.create(
            "MissingTvBanner",
            "Missing TV Banner",
            "A TV application must provide a home screen banner for each localization if it "
                    + "includes a Leanback launcher intent filter. The banner is the app launch "
                    + "point that appears on the home screen in the apps and games rows.\n"
                    + "Reference: https://developer.android.com/training/tv/start/start.html#banner",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TAG_APPLICATION = "application";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_ACTIVITY_ALIAS = "activity-alias";
    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String TAG_CATEGORY = "category";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_BANNER = "banner";
    private static final String ATTR_REQUIRED = "required";
    private static final String LEANBACK_CATEGORY = "android.intent.category.LEANBACK_LAUNCHER";
    private static final String MAIN_ACTION = "android.intent.action.MAIN";
    private static final String TV_FEATURE = "android.hardware.type.television";

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_APPLICATION);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!TAG_APPLICATION.equals(element.getTagName())) {
            return;
        }

        Element root = element.getOwnerDocument().getDocumentElement();
        if (root == null) {
            return;
        }

        if (!requiresTvBanner(root)) {
            return;
        }

        if (!element.hasAttributeNS(ANDROID_URI, ATTR_BANNER)) {
            context.report(
                    MISSING_TV_BANNER,
                    element,
                    context.getLocation(element),
                    "TV application is missing an `android:banner` attribute on the `<application>` element");
        }
    }

    private static boolean requiresTvBanner(Element root) {
        return hasLeanbackLauncher(root) || declaresRequiredTvFeature(root);
    }

    private static boolean declaresRequiredTvFeature(Element root) {
        NodeList features = root.getElementsByTagName(TAG_USES_FEATURE);
        for (int i = 0; i < features.getLength(); i++) {
            Element feature = (Element) features.item(i);
            if (TV_FEATURE.equals(feature.getAttributeNS(ANDROID_URI, ATTR_NAME))) {
                String required = feature.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
                if (required.isEmpty() || Boolean.parseBoolean(required)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasLeanbackLauncher(Element root) {
        return hasLeanbackLauncherForTag(root, TAG_ACTIVITY)
                || hasLeanbackLauncherForTag(root, TAG_ACTIVITY_ALIAS);
    }

    private static boolean hasLeanbackLauncherForTag(Element root, String tag) {
        NodeList components = root.getElementsByTagName(tag);
        for (int i = 0; i < components.getLength(); i++) {
            Element component = (Element) components.item(i);
            NodeList filters = component.getElementsByTagName(TAG_INTENT_FILTER);
            for (int j = 0; j < filters.getLength(); j++) {
                Element filter = (Element) filters.item(j);
                if (isLeanbackLauncherFilter(filter)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isLeanbackLauncherFilter(Element filter) {
        boolean hasMain = false;
        boolean hasLeanback = false;
        NodeList children = filter.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            String name = childElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (TAG_ACTION.equals(childElement.getTagName()) && MAIN_ACTION.equals(name)) {
                hasMain = true;
            } else if (TAG_CATEGORY.equals(childElement.getTagName())
                    && LEANBACK_CATEGORY.equals(name)) {
                hasLeanback = true;
            }
        }
        return hasMain && hasLeanback;
    }
}