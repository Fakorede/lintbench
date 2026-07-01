package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class WearStandaloneAppDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "WearStandaloneAppFlag",
            "Invalid or missing Wear standalone app flag",
            "Wearable apps should specify whether they can work standalone, " +
            "without a phone app. Add a valid meta-data entry for " +
            "\"com.google.android.wearable.standalone\" to your application " +
            "element and set the value to \"true\" or \"false\".",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    WearStandaloneAppDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    );

    @Override
    public void visitDocument(@NotNull XmlContext context, @NotNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        boolean isWearApp = false;
        NodeList usesFeatures = root.getElementsByTagName("uses-feature");
        for (int i = 0; i < usesFeatures.getLength(); i++) {
            Element usesFeature = (Element) usesFeatures.item(i);
            String name = usesFeature.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if ("android.hardware.type.watch".equals(name)) {
                isWearApp = true;
                break;
            }
        }

        if (!isWearApp) {
            NodeList usesLibraries = root.getElementsByTagName("uses-library");
            for (int i = 0; i < usesLibraries.getLength(); i++) {
                Element usesLibrary = (Element) usesLibraries.item(i);
                String name = usesLibrary.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                if ("com.google.android.wearable".equals(name)) {
                    isWearApp = true;
                    break;
                }
            }
        }

        if (!isWearApp) {
            return;
        }

        NodeList applications = root.getElementsByTagName("application");
        if (applications.getLength() == 0) {
            return;
        }
        Element application = (Element) applications.item(0);

        NodeList metaDatas = application.getElementsByTagName("meta-data");
        Element standaloneMeta = null;
        for (int i = 0; i < metaDatas.getLength(); i++) {
            Element metaData = (Element) metaDatas.item(i);
            String name = metaData.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if ("com.google.android.wearable.standalone".equals(name)) {
                standaloneMeta = metaData;
                break;
            }
        }

        if (standaloneMeta == null) {
            context.report(ISSUE, application, context.getNameLocation(application),
                    "Missing `com.google.android.wearable.standalone` meta-data tag");
            return;
        }

        String value = standaloneMeta.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_VALUE);
        if (!"true".equals(value) && !"false".equals(value)) {
            context.report(ISSUE, standaloneMeta, context.getValueLocation(standaloneMeta),
                    "Expect `true` or `false` for `android:value` attribute");
        }
    }
}