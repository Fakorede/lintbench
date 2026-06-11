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

import java.util.HashMap;
import java.util.Map;

public class DuplicateResourceDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "DuplicateResources",
            "Duplicate definitions of resources in the same resource folder are likely errors.",
            "Defining the same resource more than once in the same resource folder is likely an error, for example attempting to add a new resource without realizing that the name is already used.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    DuplicateResourceDetector.class,
                    true));

    private Map<String, Element> resourceMap;

    @Override
    public void beforeCheck(String projectPath) {
        super.beforeCheck(projectPath);
        resourceMap = new HashMap<>();
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String resourceName = getResourceName(element);
        if (resourceName != null && resourceMap.containsKey(resourceName)) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Duplicate definition of resource: " + resourceName);
        } else {
            resourceMap.put(resourceName, element);
        }
    }

    @Override
    public void visitElementAfter(XmlContext context, Element element) {}

    @Override
    public Collection<String> getApplicableAttributes() {
        return null;
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {}

    @Override
    public void visitDocument(XmlContext context, Document document) {}

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return true;
    }

    private String getResourceName(Element element) {
        if (element.hasAttribute("name")) {
            return element.getAttribute("name");
        }
        return null;
    }
}