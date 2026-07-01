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
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String LEANBACK_FEATURE = "android.software.leanback";
    private static final String LEANBACK_LAUNCHER =
            "android.intent.category.LEANBACK_LAUNCHER";
    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_CATEGORY = "category";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_APPLICATION = "application";
    private static final String ATTR_NAME = "android:name";

    public static final Issue MISSING_LEANBACK_SUPPORT = Issue.create(
            "MissingLeanbackSupport",
            "Missing Leanback Support",
            "The manifest should declare the use of the Leanback user interface " +
            "required by Android TV.\n\n" +
            "To fix this, add\n" +
            "```xml\n" +
            "`<uses-feature android:name=\"android.software.leanback\"\n" +
            "               android:required=\"false\" />`\n" +
            "```\n" +
            "to your manifest.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    AndroidTvDetector.class,
                    Scope.MANIFEST_SCOPE
            ))
            .addMoreInfo("https://developer.android.com/training/tv/start/start.html#leanback-req");

    public AndroidTvDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES_FEATURE);
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        // Check if this manifest targets Android TV by looking for LEANBACK_LAUNCHER category
        if (!hasLeanbackLauncherCategory(root)) {
            return;
        }

        // Check if leanback uses-feature is declared
        if (!hasLeanbackFeature(root)) {
            context.report(
                    MISSING_LEANBACK_SUPPORT,
                    root,
                    context.getLocation(root),
                    "Manifest should declare a `<uses-feature android:name=\"android.software.leanback\" android:required=\"false\" />` entry"
            );
        }
    }

    private boolean hasLeanbackLauncherCategory(Element root) {
        // Look for <application> element
        NodeList applicationNodes = root.getElementsByTagName(TAG_APPLICATION);
        for (int i = 0; i < applicationNodes.getLength(); i++) {
            Element application = (Element) applicationNodes.item(i);
            // Look for <activity> elements
            NodeList activityNodes = application.getElementsByTagName(TAG_ACTIVITY);
            for (int j = 0; j < activityNodes.getLength(); j++) {
                Element activity = (Element) activityNodes.item(j);
                // Look for <intent-filter> elements
                NodeList intentFilterNodes = activity.getElementsByTagName(TAG_INTENT_FILTER);
                for (int k = 0; k < intentFilterNodes.getLength(); k++) {
                    Element intentFilter = (Element) intentFilterNodes.item(k);
                    // Look for <category> elements
                    NodeList categoryNodes = intentFilter.getElementsByTagName(TAG_CATEGORY);
                    for (int l = 0; l < categoryNodes.getLength(); l++) {
                        Element category = (Element) categoryNodes.item(l);
                        String name = category.getAttribute(ATTR_NAME);
                        if (LEANBACK_LAUNCHER.equals(name)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean hasLeanbackFeature(Element root) {
        NodeList usesFeatureNodes = root.getElementsByTagName(TAG_USES_FEATURE);
        for (int i = 0; i < usesFeatureNodes.getLength(); i++) {
            Element usesFeature = (Element) usesFeatureNodes.item(i);
            String name = usesFeature.getAttribute(ATTR_NAME);
            if (LEANBACK_FEATURE.equals(name)) {
                return true;
            }
        }
        return false;
    }
}