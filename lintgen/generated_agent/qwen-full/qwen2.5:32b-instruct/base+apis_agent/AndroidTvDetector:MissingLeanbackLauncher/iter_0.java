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
            "MissingLeanbackLauncherIntentFilter",
            "An application intended to run on TV devices must declare a launcher activity for TV in its manifest using an `android.intent.category.LEANBACK_LAUNCHER` intent filter.",
            "This issue reports any AndroidManifest.xml files that do not contain an activity with the `android.intent.category.LEANBACK_LAUNCHER` intent filter, which is required for applications intended to run on TV devices.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(AndroidTvDetector.class, true)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("activity");
    }

    private boolean hasLeanbackLauncherIntentFilter(Element activityElement) {
        NodeList intentFilters = activityElement.getElementsByTagName("intent-filter");
        for (int i = 0; i < intentFilters.getLength(); i++) {
            Element intentFilter = (Element) intentFilters.item(i);
            NodeList categories = intentFilter.getElementsByTagName("category");
            for (int j = 0; j < categories.getLength(); j++) {
                Element category = (Element) categories.item(j);
                Attr nameAttr = category.getAttributeNode("android:name");
                if (nameAttr != null && "android.intent.category.LEANBACK_LAUNCHER".equals(nameAttr.getValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!hasLeanbackLauncherIntentFilter(element)) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Activity does not have a `android.intent.category.LEANBACK_LAUNCHER` intent filter");
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.MANIFEST == folderType;
    }
}