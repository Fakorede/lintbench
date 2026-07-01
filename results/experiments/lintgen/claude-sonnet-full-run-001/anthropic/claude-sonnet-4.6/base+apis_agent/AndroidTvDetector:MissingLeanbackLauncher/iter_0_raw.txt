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

    public static final Issue ISSUE_MISSING_LEANBACK_LAUNCHER = Issue.create(
            "MissingLeanbackLauncher",
            "Missing Leanback Launcher Intent Filter",
            "An application intended to run on TV devices must declare a launcher activity " +
            "for TV in its manifest using a `android.intent.category.LEANBACK_LAUNCHER` " +
            "intent filter.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    AndroidTvDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    ).addMoreInfo("https://developer.android.com/training/tv/start/start.html#tv-activity");

    private static final String ANDROID_MANIFEST_XML = "AndroidManifest.xml";
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
    public void visitDocument(XmlContext context, Document document) {
        // Only check AndroidManifest.xml
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Check if this manifest declares leanback feature
        if (!TAG_APPLICATION.equals(element.getTagName())) {
            return;
        }

        // Check if the manifest uses leanback feature
        Element manifestElement = (Element) element.getParentNode();
        if (manifestElement == null) {
            return;
        }

        boolean usesLeanback = false;
        NodeList usesFeatureNodes = manifestElement.getElementsByTagName(TAG_USES_FEATURE);
        for (int i = 0; i < usesFeatureNodes.getLength(); i++) {
            Node node = usesFeatureNodes.item(i);
            if (node instanceof Element) {
                Element usesFeature = (Element) node;
                String featureName = usesFeature.getAttributeNS(ANDROID_NS, ATTR_NAME);
                if (LEANBACK_FEATURE.equals(featureName)) {
                    usesLeanback = true;
                    break;
                }
            }
        }

        if (!usesLeanback) {
            // Not a TV app, skip
            return;
        }

        // Check if any activity has a LEANBACK_LAUNCHER intent filter
        boolean hasLeanbackLauncher = false;
        NodeList activityNodes = element.getElementsByTagName(TAG_ACTIVITY);
        for (int i = 0; i < activityNodes.getLength(); i++) {
            Node node = activityNodes.item(i);
            if (node instanceof Element) {
                Element activity = (Element) node;
                if (activityHasLeanbackLauncher(activity)) {
                    hasLeanbackLauncher = true;
                    break;
                }
            }
        }

        if (!hasLeanbackLauncher) {
            context.report(
                    ISSUE_MISSING_LEANBACK_LAUNCHER,
                    element,
                    context.getLocation(element),
                    "Leanback apps should have a `android.intent.category.LEANBACK_LAUNCHER` " +
                    "intent filter"
            );
        }
    }

    private boolean activityHasLeanbackLauncher(Element activity) {
        NodeList intentFilterNodes = activity.getElementsByTagName(TAG_INTENT_FILTER);
        for (int i = 0; i < intentFilterNodes.getLength(); i++) {
            Node node = intentFilterNodes.item(i);
            if (node instanceof Element) {
                Element intentFilter = (Element) node;
                if (intentFilterHasLeanbackLauncher(intentFilter)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean intentFilterHasLeanbackLauncher(Element intentFilter) {
        NodeList categoryNodes = intentFilter.getElementsByTagName(TAG_CATEGORY);
        for (int i = 0; i < categoryNodes.getLength(); i++) {
            Node node = categoryNodes.item(i);
            if (node instanceof Element) {
                Element category = (Element) node;
                String categoryName = category.getAttributeNS(ANDROID_NS, ATTR_NAME);
                if (LEANBACK_LAUNCHER_CATEGORY.equals(categoryName)) {
                    return true;
                }
            }
        }
        return false;
    }
}