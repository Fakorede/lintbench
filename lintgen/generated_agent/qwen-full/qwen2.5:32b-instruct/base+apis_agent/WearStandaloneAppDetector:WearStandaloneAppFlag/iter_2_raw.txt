package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

public class WearStandaloneAppDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "WearStandaloneFlag",
            "Invalid or missing Wear standalone app flag",
            "Wearable apps should specify whether they can work standalone, without a phone app. Add a valid meta-data entry for `com.google.android.wearable.standalone` to your application element and set the value to `true` or `false`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    WearStandaloneAppDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList("application");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if ("application".equals(element.getTagName())) {
            boolean found = false;
            for (Attr attr : getAttributeList(element)) {
                String name = attr.getName();
                if ("android:theme".equals(name)) {
                    continue; // Skip theme attributes
                }
            }

            Element metaElement = getFirstChildWithTag(element, "meta-data");
            while (metaElement != null) {
                Attr metaDataName = getAttribute(metaElement, "android:name");
                Attr metaDataValue = getAttribute(metaElement, "android:value");

                if ("com.google.android.wearable.standalone".equals(getAttributeValue(metaDataName))) {
                    found = true;
                    String value = getAttributeValue(metaDataValue);
                    if (!"true".equals(value) && !"false".equals(value)) {
                        context.report(ISSUE, element, context.getLocation(element),
                                "Invalid value for `com.google.android.wearable.standalone`. Value should be either `true` or `false`.");
                    }
                }

                metaElement = getNextSiblingWithTag(metaElement, "meta-data");
            }

            if (!found) {
                context.report(ISSUE, element, context.getLocation(element),
                        "Missing `com.google.android.wearable.standalone` flag in your application manifest. Add a valid meta-data entry.");
            }
        }
    }

    private List<Attr> getAttributeList(Element element) {
        return XmlUtils.getAttributeList(element);
    }

    private Element getFirstChildWithTag(Element parent, String tagName) {
        return XmlUtils.getFirstChildWithTag(parent, tagName);
    }

    private Attr getAttribute(Element element, String attributeName) {
        return XmlUtils.getAttribute(element, attributeName);
    }

    private String getAttributeValue(Attr attr) {
        return attr != null ? attr.getValue() : null;
    }

    private Element getNextSiblingWithTag(Element sibling, String tagName) {
        return XmlUtils.getNextSiblingWithTag(sibling, tagName);
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.MANIFEST == folderType;
    }
}