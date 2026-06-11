package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingLeanbackSupport",
            "The manifest should declare the use of the Leanback user interface required by Android TV.",
            "To fix this, add `<uses-feature android:name=\"android.software.leanback\" android:required=\"false\" />` to your manifest.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(AndroidTvDetector.class, true)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("manifest");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if ("manifest".equals(element.getTagName())) {
            boolean leanbackFeatureFound = false;
            List<Element> usesFeatures = context.getDom().getElementsByTagName("uses-feature").getListOfElements();
            for (Element feature : usesFeatures) {
                Attr nameAttr = feature.getAttributeNode("android:name");
                if (nameAttr != null && "android.software.leanback".equals(nameAttr.getValue())) {
                    leanbackFeatureFound = true;
                    break;
                }
            }

            if (!leanbackFeatureFound) {
                context.report(ISSUE, element, context.getLocation(element),
                        "The manifest should declare the use of the Leanback user interface required by Android TV.");
            }
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.MANIFEST == folderType;
    }
}