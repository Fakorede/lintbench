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
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
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
            new Implementation(WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_MANIFEST);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!SdkConstants.TAG_MANIFEST.equals(element.getTagName())) {
            return;
        }

        boolean isWearApp = false;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && SdkConstants.TAG_USES_FEATURE.equals(child.getNodeName())) {
                Element feature = (Element) child;
                if ("android.hardware.type.watch".equals(feature.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME))) {
                    isWearApp = true;
                    break;
                }
            }
        }

        if (!isWearApp) {
            return;
        }

        Element application = null;
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && SdkConstants.TAG_APPLICATION.equals(child.getNodeName())) {
                application = (Element) child;
                break;
            }
        }

        if (application == null) {
            return;
        }

        Element standaloneMetadata = null;
        NodeList appChildren = application.getChildNodes();
        for (int i = 0; i < appChildren.getLength(); i++) {
            Node child = appChildren.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && SdkConstants.TAG_META_DATA.equals(child.getNodeName())) {
                Element metadata = (Element) child;
                if ("com.google.android.wearable.standalone".equals(metadata.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME))) {
                    standaloneMetadata = metadata;
                    break;
                }
            }
        }

        if (standaloneMetadata == null) {
            context.report(
                    ISSUE,
                    application,
                    context.getNameLocation(application),
                    "Expect `com.google.android.wearable.standalone` `<meta-data>` to be defined"
            );
        } else {
            String value = standaloneMetadata.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_VALUE);
            if (!"true".equals(value) && !"false".equals(value)) {
                Attr valueAttr = standaloneMetadata.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_VALUE);
                Location location = valueAttr != null ? context.getLocation(valueAttr) : context.getLocation(standaloneMetadata);
                context.report(
                        ISSUE,
                        standaloneMetadata,
                        location,
                        "The placeholder/value for `com.google.android.wearable.standalone` must be `true` or `false`"
                );
            }
        }
    }
}