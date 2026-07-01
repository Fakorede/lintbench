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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    private static final String LEANBACK_LAUNCHER_CATEGORY =
            "android.intent.category.LEANBACK_LAUNCHER";
    private static final String MAIN_ACTION = "android.intent.action.MAIN";

    public static final Issue ISSUE =
            Issue.create(
                    "MissingTvBanner",
                    "Missing TV Banner",
                    "A TV application that includes a Leanback launcher intent filter must "
                            + "provide an `android:banner` on the application element. The banner "
                            + "is the app launch point that appears on the home screen in the "
                            + "apps and games rows. A banner asset (320 x 180 dp, xhdpi) should "
                            + "also be provided in the application resources for every "
                            + "localization.",
                    "https://developer.android.com/training/tv/start/start.html#banner",
                    Category.CORRECTNESS,
                    8,
                    Severity.WARNING,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(SdkConstants.TAG_APPLICATION);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!SdkConstants.TAG_APPLICATION.equals(element.getLocalName())) {
            return;
        }

        if (!hasLeanbackLauncher(element)) {
            return;
        }

        String banner =
                element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_BANNER);
        if (banner == null || banner.isEmpty()) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing TV banner: TV apps that include a Leanback launcher intent filter "
                            + "must specify an `android:banner` attribute on the application "
                            + "element.");
        }
    }

    private static boolean hasLeanbackLauncher(Element application) {
        NodeList children = application.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String tag = node.getLocalName();
            if (SdkConstants.TAG_ACTIVITY.equals(tag)
                    || SdkConstants.TAG_ACTIVITY_ALIAS.equals(tag)) {
                if (hasLeanbackLauncherFilter((Element) node)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasLeanbackLauncherFilter(Element component) {
        NodeList children = component.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            if (!SdkConstants.TAG_INTENT_FILTER.equals(node.getLocalName())) {
                continue;
            }
            boolean hasMain = false;
            boolean hasLeanback = false;
            NodeList filterChildren = node.getChildNodes();
            for (int j = 0; j < filterChildren.getLength(); j++) {
                Node child = filterChildren.item(j);
                if (child.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                String tag = child.getLocalName();
                String name =
                        ((Element) child)
                                .getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                if (SdkConstants.TAG_ACTION.equals(tag) && MAIN_ACTION.equals(name)) {
                    hasMain = true;
                } else if (SdkConstants.TAG_CATEGORY.equals(tag)
                        && LEANBACK_LAUNCHER_CATEGORY.equals(name)) {
                    hasLeanback = true;
                }
            }
            if (hasMain && hasLeanback) {
                return true;
            }
        }
        return false;
    }
}