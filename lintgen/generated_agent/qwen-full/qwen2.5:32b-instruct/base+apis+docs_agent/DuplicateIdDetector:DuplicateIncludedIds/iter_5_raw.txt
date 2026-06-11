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
import java.util.HashMap;
import java.util.Map;

public class DuplicateIdDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "DuplicateIdsInIncludedLayouts",
            "Duplicate ids across layouts combined with include tags can cause unexpected behavior.",
            "If two layouts are combined using the `<include>` tag, their view IDs must be unique within any chain of included layouts. Otherwise, `Activity#findViewById()` may return an unexpected view.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(DuplicateIdDetector.class, Scope.MANIFEST_SCOPE));

    private Map<String, Element> idMap = new HashMap<>();

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("merge");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if ("include".equals(element.getTagName())) {
            Attr layoutAttr = element.getAttributeNode("layout");
            if (layoutAttr != null) {
                String includedLayout = layoutAttr.getValue();
                // Clear the idMap for each new include to ensure uniqueness within this scope
                idMap.clear();
                context.getDriver().checkFile(context.getEnvironment(), includedLayout);
            }
        } else if ("merge".equals(element.getTagName())) {
            visitMergeElement(context, element);
        }
    }

    private void visitMergeElement(XmlContext context, Element mergeElement) {
        for (int i = 0; i < mergeElement.getChildNodes().getLength(); i++) {
            Object item = mergeElement.getChildNodes().item(i);
            if (item instanceof Element childElement) {
                Attr idAttr = childElement.getAttributeNode("android:id");
                if (idAttr != null) {
                    String idValue = idAttr.getValue();
                    if (idMap.containsKey(idValue)) {
                        context.report(ISSUE, mergeElement, context.getLocation(mergeElement),
                                "Duplicate ID found: " + idValue);
                    } else {
                        idMap.put(idValue, childElement);
                    }
                }
            }
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.LAYOUT.equals(folderType);
    }
}