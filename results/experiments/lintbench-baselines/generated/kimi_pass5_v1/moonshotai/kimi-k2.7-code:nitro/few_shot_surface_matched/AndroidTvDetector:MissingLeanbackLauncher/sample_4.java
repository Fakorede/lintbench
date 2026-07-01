package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
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

    public static final Issue ISSUE =
            Issue.create(
                    "MissingLeanbackLauncher",
                    "Missing Leanback Launcher Intent Filter",
                    "Apps that are intended to run on TV devices must include an activity (or"
                            + " activity alias) that has an intent filter with"
                            + " `android.intent.action.MAIN` and"
                            + " `android.intent.category.LEANBACK_LAUNCHER`. This intent filter"
                            + " identifies the entry point for the app on Android TV. If your"
                            + " manifest declares the `android.hardware.type.television` feature,"
                            + " you must also declare this TV launcher activity. See"
                            + " https://developer.android.com/training/tv/start/start.html#tv-activity",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final String TV_FEATURE = "android.hardware.type.television";
    private static final String ACTION_MAIN = "android.intent.action.MAIN";
    private static final String CATEGORY_LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    private boolean hasTvFeature;
    private boolean hasLeanbackLauncher;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("uses-feature", "activity", "activity-alias");
    }

    @Override
    public void beforeCheckFile(XmlContext context) {
        hasTvFeature = false;
        hasLeanbackLauncher = false;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        if ("uses-feature".equals(tag)) {
            String name = getAttribute(element, "name");
            if (TV_FEATURE.equals(name)) {
                hasTvFeature = true;
            }
        } else if ("activity".equals(tag) || "activity-alias".equals(tag)) {
            if (containsLeanbackLauncher(element)) {
                hasLeanbackLauncher = true;
            }
        }
    }

    private static boolean containsLeanbackLauncher(Element activity) {
        NodeList filters = activity.getElementsByTagName("intent-filter");
        for (int i = 0; i < filters.getLength(); i++) {
            Element filter = (Element) filters.item(i);
            boolean hasMain = false;
            boolean hasLeanback = false;

            NodeList children = filter.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                Node child = children.item(j);
                if (child.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element childElement = (Element) child;
                String childTag = childElement.getTagName();
                if ("action".equals(childTag)) {
                    String name = getAttribute(childElement, "name");
                    if (ACTION_MAIN.equals(name)) {
                        hasMain = true;
                    }
                } else if ("category".equals(childTag)) {
                    String name = getAttribute(childElement, "name");
                    if (CATEGORY_LEANBACK_LAUNCHER.equals(name)) {
                        hasLeanback = true;
                    }
                }
            }

            if (hasMain && hasLeanback) {
                return true;
            }
        }
        return false;
    }

    private static String getAttribute(Element element, String localName) {
        String value = element.getAttributeNS(ANDROID_NS, localName);
        if (value == null || value.isEmpty()) {
            value = element.getAttribute("android:" + localName);
        }
        return value;
    }

    @Override
    public void afterCheckFile(XmlContext context) {
        if (hasTvFeature && !hasLeanbackLauncher) {
            Element root = context.document.getDocumentElement();
            context.report(
                    ISSUE,
                    root,
                    context.getLocation(root),
                    "TV apps must declare a launcher activity with an"
                            + " android.intent.category.LEANBACK_LAUNCHER intent filter");
        }
    }
}