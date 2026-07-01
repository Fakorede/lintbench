package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingLeanbackLauncher",
            "Missing Leanback Launcher Intent Filter",
            "An application intended to run on TV devices must declare a launcher activity " +
            "for TV in its manifest using a `android.intent.category.LEANBACK_LAUNCHER` intent filter.",
            Category.COMPLIANCE,
            5,
            Severity.ERROR,
            new Implementation(
                    AndroidTvDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    );

    @Override
    public void visitDocument(XmlContext context, Document document) {
        boolean usesLeanbackFeature = false;
        boolean hasLeanbackLauncher = false;
        Element tvFeatureElement = null;

        NodeList usesFeatures = document.getElementsByTagName("uses-feature");
        for (int i = 0; i < usesFeatures.getLength(); i++) {
            Element element = (Element) usesFeatures.item(i);
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if ("android.software.leanback".equals(name) || "android.hardware.type.television".equals(name)) {
                usesLeanbackFeature = true;
                tvFeatureElement = element;
            }
        }

        NodeList categories = document.getElementsByTagName("category");
        for (int i = 0; i < categories.getLength(); i++) {
            Element element = (Element) categories.item(i);
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                hasLeanbackLauncher = true;
                break;
            }
        }

        if (usesLeanbackFeature && !hasLeanbackLauncher) {
            Location location;
            if (tvFeatureElement != null) {
                location = context.getNameLocation(tvFeatureElement);
            } else {
                Element root = document.getDocumentElement();
                location = root != null ? context.getNameLocation(root) : context.getLocation(document);
            }
            context.report(ISSUE, location, "Missing `android.intent.category.LEANBACK_LAUNCHER` intent filter required for TV applications");
        }
    }
}