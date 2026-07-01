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

    private static final String ANDROID_MANIFEST_XML = "AndroidManifest.xml";
    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String TAG_CATEGORY = "category";
    private static final String ATTR_NAME = "android:name";
    private static final String LEANBACK_FEATURE = "android.software.leanback";
    private static final String LEANBACK_LAUNCHER_CATEGORY = "android.intent.category.LEANBACK_LAUNCHER";

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
            new Implementation(
                    AndroidTvDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    ).addMoreInfo("https://developer.android.com/training/tv/start/start.html#leanback-req");

    public AndroidTvDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_CATEGORY);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttribute(ATTR_NAME);
        if (!LEANBACK_LAUNCHER_CATEGORY.equals(name)) {
            return;
        }

        // This is a TV app (has LEANBACK_LAUNCHER category). Check for leanback feature.
        Document document = context.document;
        if (document == null) {
            return;
        }

        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        boolean hasLeanbackFeature = false;
        NodeList usesFeatures = root.getElementsByTagName(TAG_USES_FEATURE);
        for (int i = 0; i < usesFeatures.getLength(); i++) {
            Element feature = (Element) usesFeatures.item(i);
            String featureName = feature.getAttribute(ATTR_NAME);
            if (LEANBACK_FEATURE.equals(featureName)) {
                hasLeanbackFeature = true;
                break;
            }
        }

        if (!hasLeanbackFeature) {
            context.report(
                    MISSING_LEANBACK_SUPPORT,
                    element,
                    context.getLocation(element),
                    "Expecting `<uses-feature android:name=\"android.software.leanback\" " +
                    "android:required=\"false\" />` tag."
            );
        }
    }
}