package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
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

    public static final Issue ISSUE = Issue.create(
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

    private static final String ANDROID_MANIFEST_XML = "AndroidManifest.xml";
    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String TAG_APPLICATION = "application";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_CATEGORY = "category";
    private static final String ATTR_NAME = "name";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String FEATURE_LEANBACK = "android.software.leanback";
    private static final String CATEGORY_LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER";

    public AndroidTvDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES_FEATURE);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Only check AndroidManifest.xml
        if (!context.file.getName().equals(ANDROID_MANIFEST_XML)) {
            return;
        }

        // Check if this is the leanback feature declaration
        String featureName = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (!FEATURE_LEANBACK.equals(featureName)) {
            return;
        }

        // This app declares leanback feature - check for LEANBACK_LAUNCHER intent filter
        Document document = element.getOwnerDocument();
        if (document == null) {
            return;
        }

        if (!hasLeanbackLauncherActivity(document)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Manifest should contain a `<activity>` tag with a " +
                    "`android.intent.category.LEANBACK_LAUNCHER` intent filter."
            );
        }
    }

    private boolean hasLeanbackLauncherActivity(Document document) {
        NodeList applicationNodes = document.getElementsByTagName(TAG_APPLICATION);
        if (applicationNodes == null || applicationNodes.getLength() == 0) {
            return false;
        }

        Element application = (Element) applicationNodes.item(0);
        NodeList children = application.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (!TAG_ACTIVITY.equals(childElement.getTagName())) {
                continue;
            }

            if (activityHasLeanbackLauncher(childElement)) {
                return true;
            }
        }

        return false;
    }

    private boolean activityHasLeanbackLauncher(Element activityElement) {
        NodeList children = activityElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (!TAG_INTENT_FILTER.equals(childElement.getTagName())) {
                continue;
            }

            if (intentFilterHasLeanbackLauncher(childElement)) {
                return true;
            }
        }
        return false;
    }

    private boolean intentFilterHasLeanbackLauncher(Element intentFilterElement) {
        NodeList children = intentFilterElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (!TAG_CATEGORY.equals(childElement.getTagName())) {
                continue;
            }

            String categoryName = childElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (CATEGORY_LEANBACK_LAUNCHER.equals(categoryName)) {
                return true;
            }
        }
        return false;
    }
}