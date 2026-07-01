package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class DuplicateResourceDetector extends ResourceXmlDetector {
    public static final Issue ISSUE = Issue.create(
            "DuplicateDefinition",
            "Duplicate definitions of resources",
            "You can define a resource multiple times in different resource folders; " +
            "that's how string translations are done, for example. However, defining " +
            "the same resource more than once in the same resource folder is likely an error, " +
            "for example attempting to add a new resource without realizing that the name is " +
            "already used, and so on.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private final Map<String, Location> mNames = new HashMap<>();

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Node parent = element.getParentNode();
        if (parent instanceof Element && "resources".equals(((Element) parent).getTagName())) {
            String name = element.getAttribute("name");
            if (name != null && !name.isEmpty()) {
                Location location = context.getLocation(element.getAttributeNode("name"));
                Location existing = mNames.put(name, location);
                if (existing != null) {
                    context.report(ISSUE, location, String.format("Duplicate resource name \"%s\"", name));
                }
            }
        }
    }

    @Override
    public void beforeCheckFile(Context context) {
        mNames.clear();
    }
}