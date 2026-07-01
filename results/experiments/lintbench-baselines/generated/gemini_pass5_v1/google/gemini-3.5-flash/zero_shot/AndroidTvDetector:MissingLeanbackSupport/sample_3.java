package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingLeanbackSupport",
            "Missing Leanback Support",
            "The manifest should declare the use of the Leanback user interface required by Android TV.\n" +
            "\n" +
            "To fix this, add\n" +
            "<uses-feature android:name=\"android.software.leanback\"\n" +
            "              android:required=\"false\" />\n" +
            "to your manifest.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        boolean hasLeanbackLauncher = false;
        NodeList categoryList = document.getElementsByTagName("category");
        for (int i = 0; i < categoryList.getLength(); i++) {
            Element category = (Element) categoryList.item(i);
            String name = category.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                hasLeanbackLauncher = true;
                break;
            }
        }

        if (!hasLeanbackLauncher) {
            return;
        }

        boolean hasLeanbackFeature = false;
        NodeList featureList = document.getElementsByTagName("uses-feature");
        for (int i = 0; i < featureList.getLength(); i++) {
            Element feature = (Element) featureList.item(i);
            String name = feature.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if ("android.software.leanback".equals(name)) {
                hasLeanbackFeature = true;
                break;
            }
        }

        if (!hasLeanbackFeature) {
            context.report(
                    ISSUE,
                    root,
                    context.getNameLocation(root),
                    "The manifest should declare the use of the Leanback user interface required by Android TV."
            );
        }
    }
}