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

    private static final String LEANBACK_FEATURE = "android.software.leanback";

    public static final Issue ISSUE = Issue.create(
            "MissingLeanbackSupport",
            "Missing Leanback support",
            "The manifest should declare the use of the Leanback user interface required by Android TV. " +
                    "To fix this, add `<uses-feature android:name=\"android.software.leanback\" " +
                    "android:required=\"false\" />` to your manifest.",
            "https://developer.android.com/training/tv/start/start.html#leanback-req",
            Category.TV,
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

        NodeList features = document.getElementsByTagName(SdkConstants.TAG_USES_FEATURE);
        for (int i = 0; i < features.getLength(); i++) {
            Element feature = (Element) features.item(i);
            String name = feature.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if (LEANBACK_FEATURE.equals(name)) {
                return;
            }
        }

        context.report(
                ISSUE,
                root,
                context.getLocation(root),
                "The manifest should declare the use of the Leanback user interface required by Android TV. " +
                        "Add `<uses-feature android:name=\"android.software.leanback\" " +
                        "android:required=\"false\" />` to your manifest."
        );
    }
}