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
            )
    ).addMoreInfo("https://developer.android.com/training/tv/start/start.html#leanback-req");

    private static final String LEANBACK_FEATURE = "android.software.leanback";
    private static final String ATTR_NAME = "name";
    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

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

        NodeList usesFeatures = root.getElementsByTagName(TAG_USES_FEATURE);
        boolean hasLeanback = false;

        for (int i = 0; i < usesFeatures.getLength(); i++) {
            Element feature = (Element) usesFeatures.item(i);
            String name = feature.getAttributeNS(ANDROID_NS, ATTR_NAME);
            if (LEANBACK_FEATURE.equals(name)) {
                hasLeanback = true;
                break;
            }
        }

        if (!hasLeanback) {
            context.report(
                    MISSING_LEANBACK_SUPPORT,
                    root,
                    context.getLocation(root),
                    "Manifest should declare a `<uses-feature android:name=\"android.software.leanback\" android:required=\"false\" />` entry"
            );
        }
    }
}