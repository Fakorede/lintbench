package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DuplicateIdDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "DuplicateIds",
            "Duplicate ids within a single layout",
            "Within a layout, id's should be unique since otherwise `findViewById()` can return an unexpected view.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private Map<String, Element> idMap;

    @Override
    public void beforeCheckFile(XmlContext context) {
        super.beforeCheckFile(context);
        if (context.getFile().getFolderType() == ResourceFolderType.LAYOUT) {
            idMap = new HashMap<>();
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Attr attrId = element.getAttributeNode("android:id");
        if (attrId != null && attrId.getValue() != null) {
            String idValue = attrId.getValue();
            if (idMap.containsKey(idValue)) {
                context.report(ISSUE, element, context.getLocation(element),
                        "Duplicate ID: " + idValue);
            } else {
                idMap.put(idValue, element);
            }
        }
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.emptyList();
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {}

    @Override
    public void visitDocument(XmlContext context, Document document) {}

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }
}