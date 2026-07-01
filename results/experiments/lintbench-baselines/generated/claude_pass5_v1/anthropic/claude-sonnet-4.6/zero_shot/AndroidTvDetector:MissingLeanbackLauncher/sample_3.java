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

/**
 * Detector for missing Leanback launcher intent filter in Android TV apps.
 */
public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String ANDROID_MANIFEST_XML = "AndroidManifest.xml";

    private static final String TAG_APPLICATION = "application";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_CATEGORY = "category";
    private static final String TAG_USES_FEATURE = "uses-feature";

    private static final String ATTR_NAME = "android:name";
    private static final String ATTR_REQUIRED = "android:required";

    private static final String FEATURE_LEANBACK = "android.software.leanback";
    private static final String CATEGORY_LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER";

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

    /**
     * Constructs a new {@link AndroidTvDetector}.
     */
    public AndroidTvDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_APPLICATION);
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        // We handle everything in visitElement
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!TAG_APPLICATION.equals(element.getTagName())) {
            return;
        }

        // Check if the manifest declares leanback feature
        Element manifestElement = (Element) element.getParentNode();
        if (manifestElement == null) {
            return;
        }

        boolean declaresLeanback = declaresLeanbackFeature(manifestElement);
        if (!declaresLeanback) {
            return;
        }

        // Check if there is any activity with a LEANBACK_LAUNCHER intent filter
        boolean hasLeanbackLauncher = hasLeanbackLauncherActivity(element);

        if (!hasLeanbackLauncher) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Expecting an `<activity>` tag declaring a `android.intent.category.LEANBACK_LAUNCHER` intent filter."
            );
        }
    }

    /**
     * Checks if the manifest declares the leanback feature (android.software.leanback).
     */
    private boolean declaresLeanbackFeature(Element manifestElement) {
        NodeList children = manifestElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (TAG_USES_FEATURE.equals(childElement.getTagName())) {
                    String name = childElement.getAttribute(ATTR_NAME);
                    if (FEATURE_LEANBACK.equals(name)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Checks if any activity in the application element has a LEANBACK_LAUNCHER intent filter.
     */
    private boolean hasLeanbackLauncherActivity(Element applicationElement) {
        NodeList children = applicationElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (TAG_ACTIVITY.equals(childElement.getTagName())) {
                    if (activityHasLeanbackLauncher(childElement)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Checks if the given activity element has a LEANBACK_LAUNCHER intent filter category.
     */
    private boolean activityHasLeanbackLauncher(Element activityElement) {
        NodeList children = activityElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (TAG_INTENT_FILTER.equals(childElement.getTagName())) {
                    if (intentFilterHasLeanbackLauncher(childElement)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Checks if the given intent-filter element has a LEANBACK_LAUNCHER category.
     */
    private boolean intentFilterHasLeanbackLauncher(Element intentFilterElement) {
        NodeList children = intentFilterElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (TAG_CATEGORY.equals(childElement.getTagName())) {
                    String name = childElement.getAttribute(ATTR_NAME);
                    if (CATEGORY_LEANBACK_LAUNCHER.equals(name)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}