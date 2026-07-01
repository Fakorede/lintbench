package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE_MISSING_LEANBACK_LAUNCHER = Issue.create(
            "MissingLeanbackLauncher",
            "Missing Leanback Launcher Intent Filter",
            "An application intended to run on TV devices must declare a launcher activity " +
            "for TV in its manifest using a `android.intent.category.LEANBACK_LAUNCHER` " +
            "intent filter.",
            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            new Implementation(
                    AndroidTvDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    ).addMoreInfo("https://developer.android.com/training/tv/start/start.html#tv-activity");

    private static final String ANDROID_MANIFEST = "AndroidManifest.xml";
    private static final String TAG_APPLICATION = "application";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_CATEGORY = "category";
    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String ATTR_NAME = "name";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String LEANBACK_LAUNCHER_CATEGORY =
            "android.intent.category.LEANBACK_LAUNCHER";
    private static final String LEANBACK_FEATURE = "android.software.leanback";

    public AndroidTvDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_APPLICATION);
    }

    @Override
    public void visitElement(XmlContext context, Element applicationElement) {
        // First check if this app declares the leanback feature (indicating it targets TV)
        Document document = applicationElement.getOwnerDocument();
        if (document == null) {
            return;
        }

        Element manifestElement = document.getDocumentElement();
        if (manifestElement == null) {
            return;
        }

        boolean declaresLeanbackFeature = false;
        NodeList manifestChildren = manifestElement.getChildNodes();
        for (int i = 0; i < manifestChildren.getLength(); i++) {
            Node child = manifestChildren.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) child;
                if (TAG_USES_FEATURE.equals(element.getTagName())) {
                    String featureName = element.getAttributeNS(ANDROID_NS, ATTR_NAME);
                    if (LEANBACK_FEATURE.equals(featureName)) {
                        declaresLeanbackFeature = true;
                        break;
                    }
                }
            }
        }

        if (!declaresLeanbackFeature) {
            return;
        }

        // Check if there is an activity with a LEANBACK_LAUNCHER intent filter
        boolean hasLeanbackLauncher = hasLeanbackLauncherActivity(applicationElement);

        if (!hasLeanbackLauncher) {
            context.report(
                    ISSUE_MISSING_LEANBACK_LAUNCHER,
                    applicationElement,
                    context.getLocation(applicationElement),
                    "Expecting an activity to have `android.intent.category.LEANBACK_LAUNCHER` " +
                    "intent filter."
            );
        }
    }

    private boolean hasLeanbackLauncherActivity(Element applicationElement) {
        NodeList children = applicationElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) child;
                if (TAG_ACTIVITY.equals(element.getTagName())) {
                    if (activityHasLeanbackLauncher(element)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean activityHasLeanbackLauncher(Element activityElement) {
        NodeList children = activityElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) child;
                if (TAG_INTENT_FILTER.equals(element.getTagName())) {
                    if (intentFilterHasLeanbackLauncher(element)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean intentFilterHasLeanbackLauncher(Element intentFilterElement) {
        NodeList children = intentFilterElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) child;
                if (TAG_CATEGORY.equals(element.getTagName())) {
                    String categoryName = element.getAttributeNS(ANDROID_NS, ATTR_NAME);
                    if (LEANBACK_LAUNCHER_CATEGORY.equals(categoryName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}