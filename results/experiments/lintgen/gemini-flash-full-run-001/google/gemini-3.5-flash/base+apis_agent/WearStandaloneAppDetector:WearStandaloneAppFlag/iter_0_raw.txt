package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class WearStandaloneAppDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "WearStandaloneAppFlag",
            "Invalid or missing Wear standalone app flag",
            "Wearable apps should specify whether they can work standalone, without a phone app. " +
            "Add a valid meta-data entry for `com.google.android.wearable.standalone` to " +
            "your application element and set the value to `true` or `false`.",
            Category.COMPLIANCE,
            6,
            Severity.WARNING,
            new Implementation(
                    WearStandaloneAppDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    );

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        boolean isWatchApp = false;
        NodeList usesFeatures = root.getElementsByTagName("uses-feature");
        for (int i = 0; i < usesFeatures.getLength(); i++) {
            Element usesFeature = (Element) usesFeatures.item(i);
            String name = usesFeature.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if ("android.hardware.type.watch".equals(name)) {
                isWatchApp = true;
                break;
            }
        }

        if (!isWatchApp) {
            return;
        }

        NodeList applications = root.getElementsByTagName("application");
        if (applications.getLength() == 0) {
            return;
        }
        Element application = (Element) applications.item(0);

        Element standaloneMetadata = null;
        NodeList metaDatas = application.getElementsByTagName("meta-data");
        for (int i = 0; i < metaDatas.getLength(); i++) {
            Element metaData = (Element) metaDatas.item(i);
            String name = metaData.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if ("com.google.android.wearable.standalone".equals(name)) {
                standaloneMetadata = metaData;
                break;
            }
        }

        if (standaloneMetadata == null) {
            context.report(
                    ISSUE,
                    application,
                    context.getNameLocation(application),
                    "Missing `com.google.android.wearable.standalone` meta-data tag"
            );
        } else {
            String value = standaloneMetadata.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_VALUE);
            if (!"true".equals(value) && !"false".equals(value)) {
                Attr valueNode = standaloneMetadata.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_VALUE);
                Location location = valueNode != null ? context.getValueLocation(valueNode) : context.getNameLocation(standaloneMetadata);
                context.report(
                        ISSUE,
                        standaloneMetadata,
                        location,
                        "Expect `true` or `false` for `com.google.android.wearable.standalone` value"
                );
            }
        }
    }
}