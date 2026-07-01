package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_USES_FEATURE;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingLeanbackSupport",
            "Missing Leanback support declaration",
            "The manifest should declare the use of the Leanback user interface required by Android TV. "
                    + "Add `<uses-feature android:name=\"android.software.leanback\" android:required=\"false\" />` to your manifest.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE))
            .setMoreInfo("https://developer.android.com/training/tv/start/start.html#leanback-req");

    @Override
    public void visitDocument(@NotNull XmlContext context, @NotNull Document document) {
        NodeList usesFeatures = document.getElementsByTagName(TAG_USES_FEATURE);
        for (int i = 0; i < usesFeatures.getLength(); i++) {
            Element element = (Element) usesFeatures.item(i);
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if ("android.software.leanback".equals(name)) {
                return;
            }
        }

        Element root = document.getDocumentElement();
        if (root != null) {
            context.report(ISSUE, context.getLocation(root),
                    "Missing Leanback support declaration; add `<uses-feature android:name=\"android.software.leanback\" android:required=\"false\" />`");
        }
    }
}