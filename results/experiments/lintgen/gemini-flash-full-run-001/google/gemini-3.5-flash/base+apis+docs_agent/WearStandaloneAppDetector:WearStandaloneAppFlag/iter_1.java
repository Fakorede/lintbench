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
import java.util.Collections;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class WearStandaloneAppDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "WearStandaloneAppFlag",
            "Invalid or missing Wear standalone app flag",
            "Wearable apps should specify whether they can work standalone, without a phone app. " +
            "Add a valid meta-data entry for `com.google.android.wearable.standalone` to " +
            "your application element and set the value to `true` or `false`.",
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
        return Collections.singletonList("manifest");
    }

    @Override
    public void visitElement(XmlContext context, Element manifest) {
        boolean isWearApp = false;
        Element application = null;

        for (Node child = manifest.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) child;
                String tagName = element.getTagName();
                if ("uses-feature".equals(tagName)) {
                    String name = element.getAttributeNS(SdkConstants.ANDROID_URI, "name");
                    if ("android.hardware.type.watch".equals(name)) {
                        isWearApp = true;
                    }
                } else if ("application".equals(tagName)) {
                    application = element;
                }
            }
        }

        if (!isWearApp || application == null) {
            return;
        }

        Element standaloneMeta = null;
        for (Node child = application.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) child;
                if ("meta-data".equals(element.getTagName())) {
                    String name = element.getAttributeNS(SdkConstants.ANDROID_URI, "name");
                    if ("com.google.android.wearable.standalone".equals(name)) {
                        standaloneMeta = element;
                        break;
                    }
                }
            }
        }

        if (standaloneMeta == null) {
            context.report(
                    ISSUE,
                    application,
                    context.getNameLocation(application),
                    "Missing `com.google.android.wearable.standalone` metadata element"
            );
        } else {
            Attr valueAttr = standaloneMeta.getAttributeNodeNS(SdkConstants.ANDROID_URI, "value");
            if (valueAttr == null) {
                context.report(
                        ISSUE,
                        standaloneMeta,
                        context.getNameLocation(standaloneMeta),
                        "Missing `android:value` attribute"
                );
            } else {
                String value = valueAttr.getValue();
                if (!"true".equals(value) && !"false".equals(value)) {
                    context.report(
                            ISSUE,
                            valueAttr,
                            context.getValueLocation(valueAttr),
                            "The `com.google.android.wearable.standalone` value must be \"true\" or \"false\""
                    );
                }
            }
        }
    }
}