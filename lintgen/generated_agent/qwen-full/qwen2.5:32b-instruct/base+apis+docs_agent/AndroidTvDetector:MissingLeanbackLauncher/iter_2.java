package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.w3c.dom.Attr;
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
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.MANIFEST.equals(folderType);
    }

    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList("activity");
    }

    private boolean hasLeanbackLauncherIntentFilter(Element activityElement) {
        for (Element child : getChildren(activityElement, "intent-filter")) {
            for (Attr attr : getAttributes(child, "category")) {
                if ("android.intent.category.LEANBACK_LAUNCHER".equals(attr.getValue())) {
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
                    "Activity is missing the `android.intent.category.LEANBACK_LAUNCHER` intent filter");
        }
    }

    private List<Element> getChildren(Element parentElement, String childName) {
        return XmlUtils.getChildren(parentElement, childName);
    }

    private List<Attr> getAttributes(Element element, String attributeName) {
        return XmlUtils.getAttributes(element, attributeName);
    }
}