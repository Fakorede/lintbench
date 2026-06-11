package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Detector.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import com.android.tools.lint.detector.api.Project;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.io.File;

public class DuplicateIdDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "DuplicateIdsInIncludedLayouts",
            "Duplicate ids across layouts combined with include tags",
            "It's okay for two independent layouts to use the same ids. However, if layouts are combined with include tags, then the id's need to be unique within any chain of included layouts, or `Activity#findViewById()` can return an unexpected view.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(DuplicateIdDetector.class, EnumSet.of(Scope.RESOURCE_FILE)));

    private Map<String, Set<Attr>> idMap = new HashMap<>();
    private List<String> includedLayouts = new ArrayList<>();

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singleton("include");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Attr layoutAttribute = getLayoutAttribute(element);
        if (layoutAttribute != null) {
            String includedLayoutName = layoutAttribute.getValue();
            includedLayouts.add(includedLayoutName);
            File includedFile = new File(context.getProject().getProjectDirectory(), "res/layout/" + includedLayoutName.replace("@layout/", "") + ".xml");
            if (includedFile.exists()) {
                context.getDriver().checkFile(context, includedFile.getAbsolutePath());
            }
        }
    }

    @Override
    public void visitElementAfter(XmlContext context, Element element) {
        for (int i = 0; i < element.getAttributes().getLength(); i++) {
            Attr attribute = (Attr) element.getAttributes().item(i);
            if ("android:id".equals(attribute.getName())) {
                String id = attribute.getValue();
                idMap.computeIfAbsent(id, k -> new HashSet<>()).add(attribute);
            }
        }

        checkForDuplicates(context);
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        for (Element child : getChildren(document.getDocumentElement())) {
            visitElementAfter(context, child);
        }
        idMap.clear();
        includedLayouts.clear();
    }

    private List<Element> getChildren(Element element) {
        var children = new ArrayList<Element>();
        for (var node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element) {
                children.add((Element) node);
            }
        }
        return children;
    }

    private Attr getLayoutAttribute(Element element) {
        for (int i = 0; i < element.getAttributes().getLength(); i++) {
            Attr attribute = (Attr) element.getAttributes().item(i);
            if ("layout".equals(attribute.getName())) {
                return attribute;
            }
        }
        return null;
    }

    private void checkForDuplicates(XmlContext context) {
        for (Set<Attr> attributes : idMap.values()) {
            if (attributes.size() > 1) {
                for (Attr attr : attributes) {
                    context.report(ISSUE, attr, context.getLocation(attr),
                            "Duplicate ID found: " + attr.getValue());
                }
            }
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.LAYOUT.equals(folderType);
    }
}