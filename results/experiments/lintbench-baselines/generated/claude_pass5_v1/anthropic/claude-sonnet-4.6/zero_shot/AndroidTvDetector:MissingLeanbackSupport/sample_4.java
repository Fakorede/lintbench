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
    private static final String ATTR_REQUIRED = "android:required";

    public static final Issue MISSING_LEANBACK_SUPPORT = Issue.create(
            "MissingLeanbackSupport",
            "Missing Leanback Support",
            "The manifest should declare the use of the Leanback user interface required by " +
            "Android TV.\n\n" +
            "To fix this, add\n" +
            "```xml\n" +
            "`<uses-feature android:name=\"android.software.leanback\"\n" +
            "               android:required=\"false\" />`\n" +
            "```\n" +
            "to your manifest.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE))
            .addMoreInfo("https://developer.android.com/training/tv/start/start.html#leanback-req");

    public AndroidTvDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES_FEATURE);
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        // Check if this manifest targets Android TV by looking for LEANBACK_LAUNCHER category
        boolean hasLeanbackLauncher = false;
        boolean hasLeanbackFeature = false;

        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        // Check for uses-feature leanback
        NodeList usesFeatures = root.getElementsByTagName(TAG_USES_FEATURE);
        for (int i = 0; i < usesFeatures.getLength(); i++) {
            Element feature = (Element) usesFeatures.item(i);
            String name = feature.getAttribute(ATTR_NAME);
            if (LEANBACK_FEATURE.equals(name)) {
                hasLeanbackFeature = true;
                break;
            }
        }

        if (hasLeanbackFeature) {
            return;
        }

        // Check for LEANBACK_LAUNCHER category in any activity's intent-filter
        NodeList activities = root.getElementsByTagName(TAG_ACTIVITY);
        outer:
        for (int i = 0; i < activities.getLength(); i++) {
            Element activity = (Element) activities.item(i);
            NodeList intentFilters = activity.getElementsByTagName(TAG_INTENT_FILTER);
            for (int j = 0; j < intentFilters.getLength(); j++) {
                Element intentFilter = (Element) intentFilters.item(j);
                NodeList categories = intentFilter.getElementsByTagName(TAG_CATEGORY);
                for (int k = 0; k < categories.getLength(); k++) {
                    Element category = (Element) categories.item(k);
                    String name = category.getAttribute(ATTR_NAME);
                    if (LEANBACK_LAUNCHER.equals(name)) {
                        hasLeanbackLauncher = true;
                        break outer;
                    }
                }
            }
        }

        if (hasLeanbackLauncher && !hasLeanbackFeature) {
            context.report(
                    MISSING_LEANBACK_SUPPORT,
                    root,
                    context.getLocation(root),
                    "Manifest should declare a `<uses-feature android:name=\"android.software.leanback\" android:required=\"false\" />` entry");
        }
    }
}