package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingLeanbackLauncher",
            "Missing Leanback Launcher Intent Filter",
            "An application intended to run on TV devices must declare a launcher " +
                    "activity for TV in its manifest using a `android.intent.category.LEANBACK_LAUNCHER` " +
                    "intent filter.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(
                    AndroidTvDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    );

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        boolean hasLeanbackFeature = false;
        boolean hasLeanbackLauncher = false;
        Element leanbackFeatureElement = null;

        NodeList usesFeatures = root.getElementsByTagName("uses-feature");
        for (int i = 0; i < usesFeatures.getLength(); i++) {
            Element element = (Element) usesFeatures.item(i);
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if ("android.software.leanback".equals(name)) {
                hasLeanbackFeature = true;
                leanbackFeatureElement = element;
                break;
            }
        }

        if (!hasLeanbackFeature) {
            return;
        }

        NodeList categories = root.getElementsByTagName("category");
        for (int i = 0; i < categories.getLength(); i++) {
            Element element = (Element) categories.item(i);
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                hasLeanbackLauncher = true;
                break;
            }
        }

        if (!hasLeanbackLauncher) {
            Element locationElement = leanbackFeatureElement != null ? leanbackFeatureElement : root;
            context.report(
                    ISSUE,
                    locationElement,
                    context.getLocation(locationElement),
                    "An application intended to run on TV devices must declare a launcher activity for TV in its manifest using a `android.intent.category.LEANBACK_LAUNCHER` intent filter."
            );
        }
    }
}