package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class WearStandaloneAppDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "WearStandaloneAppFlag",
            "Invalid or missing Wear standalone app flag",
            "Wearable apps should specify whether they can work standalone, without a phone app. " +
            "Add a valid meta-data entry for `com.google.android.wearable.standalone` to your " +
            "application element and set the value to `true` or `false`.",
            Category.CORRECTNESS,
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
    public void visitElement(XmlContext context, Element element) {
        if (!SdkConstants.TAG_APPLICATION.equals(element.getTagName())) {
            return;
        }

        Document document = element.getOwnerDocument();
        if (document == null || !isWearProject(document)) {
            return;
        }

        boolean foundStandaloneFlag = false;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "meta-data".equals(child.getNodeName())) {
                Element metaData = (Element) child;
                String name = metaData.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                if ("com.google.android.wearable.standalone".equals(name)) {
                    foundStandaloneFlag = true;
                    String value = metaData.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_VALUE);
                    if (!"true".equals(value) && !"false".equals(value)) {
                        context.report(
                                ISSUE,
                                metaData,
                                context.getLocation(metaData),
                                "The `android:value` attribute must be `true` or `false` for `com.google.android.wearable.standalone`"
                        );
                    }
                    break;
                }
            }
        }

        if (!foundStandaloneFlag) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing `com.google.android.wearable.standalone` meta-data tag in the manifest"
            );
        }
    }

    private boolean isWearProject(Document document) {
        NodeList usesFeatures = document.getElementsByTagName(SdkConstants.TAG_USES_FEATURE);
        for (int i = 0; i < usesFeatures.getLength(); i++) {
            Element element = (Element) usesFeatures.item(i);
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if ("android.hardware.type.watch".equals(name)) {
                return true;
            }
        }
        return false;
    }
}