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
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class WearStandaloneAppDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "WearStandaloneAppFlag",
            "Invalid or missing Wear standalone app flag",
            "Wearable apps should specify whether they can work standalone, " +
            "without a phone app. Add a valid meta-data entry for " +
            "`com.google.android.wearable.standalone` to your application " +
            "element and set the value to `true` or `false`.",
            Category.COMPLIANCE,
            6,
            Severity.WARNING,
            new Implementation(
                    WearStandaloneAppDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_APPLICATION);
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        boolean isWearApp = false;
        NodeList usesFeatures = root.getElementsByTagName(SdkConstants.TAG_USES_FEATURE);
        for (int i = 0; i < usesFeatures.getLength(); i++) {
            Element usesFeature = (Element) usesFeatures.item(i);
            String name = usesFeature.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if ("android.hardware.type.watch".equals(name)) {
                isWearApp = true;
                break;
            }
        }

        NodeList applications = root.getElementsByTagName(SdkConstants.TAG_APPLICATION);
        if (applications.getLength() == 0) {
            return;
        }
        Element application = (Element) applications.item(0);

        Element standaloneMetadata = null;
        NodeList childNodes = application.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && SdkConstants.TAG_META_DATA.equals(child.getNodeName())) {
                Element metaData = (Element) child;
                String name = metaData.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                if ("com.google.android.wearable.standalone".equals(name)) {
                    standaloneMetadata = metaData;
                    break;
                }
            }
        }

        if (standaloneMetadata == null) {
            if (isWearApp) {
                Location location = context.getNameLocation(application);
                context.report(ISSUE, application, location,
                        "Missing `com.google.android.wearable.standalone` meta-data tag");
            }
        } else {
            String value = standaloneMetadata.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_VALUE);
            if (!"true".equals(value) && !"false".equals(value)) {
                Location location = context.getValueLocation(standaloneMetadata);
                context.report(ISSUE, standaloneMetadata, location,
                        "Expect `true` or `false` for `android:value` in `com.google.android.wearable.standalone` meta-data");
            }
        }
    }
}