package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
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
                    true));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("application");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if ("application".equals(element.getTagName())) {
            boolean found = false;
            List<Attr> attrs = XmlUtils.getAttributeList(element);
            for (Attr attr : attrs) {
                String name = attr.getName();
                if ("android:theme".equals(name)) {
                    continue; // Skip theme attributes
                }
                Element metaElement = XmlUtils.getFirstChildWithTag(element, "meta-data");
                while (metaElement != null) {
                    Attr metaDataName = XmlUtils.getAttribute(metaElement, "android:name");
                    Attr metaDataValue = XmlUtils.getAttribute(metaElement, "android:value");

                    if ("com.google.android.wearable.standalone".equals(XmlUtils.getAttributeValue(metaDataName))) {
                        found = true;
                        String value = XmlUtils.getAttributeValue(metaDataValue);
                        if (!"true".equals(value) && !"false".equals(value)) {
                            context.report(ISSUE, element, context.getLocation(element),
                                    "Invalid value for `com.google.android.wearable.standalone`. Value should be either `true` or `false`.");
                        }
                    }
                    metaElement = XmlUtils.getNextSiblingWithTag(metaElement, "meta-data");
                }

                if (!found) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "Missing `com.google.android.wearable.standalone` flag in your application manifest. Add a valid meta-data entry.");
                }
            }
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.MANIFEST == folderType;
    }
}