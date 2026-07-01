package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingLeanbackSupport",
            "Missing Leanback Support",
            "The manifest should declare the use of the Leanback user interface " +
            "required by Android TV.\n\n" +
            "To fix this, add\n" +
            "```xml\n" +
            "<uses-feature android:name=\"android.software.leanback\"\n" +
            "              android:required=\"false\" />\n" +
            "```\n" +
            "to your manifest.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    AndroidTvDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    );

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        boolean hasLeanbackLauncher = false;
        NodeList categoryNodes = document.getElementsByTagName(SdkConstants.TAG_CATEGORY);
        for (int i = 0; i < categoryNodes.getLength(); i++) {
            Element category = (Element) categoryNodes.item(i);
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
        NodeList usesFeatureNodes = document.getElementsByTagName("uses-feature");
        for (int i = 0; i < usesFeatureNodes.getLength(); i++) {
            Element usesFeature = (Element) usesFeatureNodes.item(i);
            String name = usesFeature.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if ("android.software.leanback".equals(name)) {
                hasLeanbackFeature = true;
                break;
            }
        }

        if (!hasLeanbackFeature) {
            LintFix fix = fix()
                    .replace()
                    .text("</manifest>")
                    .with("    <uses-feature android:name=\"android.software.leanback\" android:required=\"false\" />\n</manifest>")
                    .build();

            context.report(
                    ISSUE,
                    root,
                    context.getLocation(root),
                    "Missing Leanback support declaration in the manifest",
                    fix
            );
        }
    }
}