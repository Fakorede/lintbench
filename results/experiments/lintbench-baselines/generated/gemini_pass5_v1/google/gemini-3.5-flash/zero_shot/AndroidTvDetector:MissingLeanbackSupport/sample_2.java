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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingLeanbackSupport",
            "Missing Leanback Support",
            "The manifest should declare the use of the Leanback user interface required by Android TV.\n\n" +
            "To fix this, add\n" +
            "`<uses-feature android:name=\"android.software.leanback\"\n" +
            "              android:required=\"false\" />`\n" +
            "to your manifest.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    AndroidTvDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    ).addMoreInfo("https://developer.android.com/training/tv/start/start.html#leanback-req");

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        boolean hasLeanbackFeature = false;
        NodeList usesFeatures = document.getElementsByTagName("uses-feature");
        for (int i = 0; i < usesFeatures.getLength(); i++) {
            Element element = (Element) usesFeatures.item(i);
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, "name");
            if ("android.software.leanback".equals(name)) {
                hasLeanbackFeature = true;
                break;
            }
        }

        if (hasLeanbackFeature) {
            return;
        }

        boolean hasLeanbackLauncher = false;
        Element launcherElement = null;
        NodeList categories = document.getElementsByTagName("category");
        for (int i = 0; i < categories.getLength(); i++) {
            Element element = (Element) categories.item(i);
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, "name");
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                hasLeanbackLauncher = true;
                launcherElement = element;
                break;
            }
        }

        if (hasLeanbackLauncher) {
            String fixSnippet = "\n    <uses-feature android:name=\"android.software.leanback\" android:required=\"false\" />";
            LintFix fix = fix()
                    .name("Add Leanback support")
                    .replace()
                    .range(context.getLocation(root))
                    .pattern("(<manifest[^>]*>)")
                    .with("\\1" + fixSnippet)
                    .reformat(true)
                    .build();

            context.report(
                    ISSUE,
                    launcherElement != null ? launcherElement : root,
                    context.getLocation(launcherElement != null ? launcherElement : root),
                    "The manifest should declare the use of the Leanback user interface required by Android TV",
                    fix
            );
        }
    }
}