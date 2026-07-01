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

import java.util.Collection;
import java.util.Collections;
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

    private final Map<String, Location> mResources = new HashMap<>();

    @Override
    public void beforeCheckProject(Context context) {
        mResources.clear();
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttribute("name");
        if (name == null || name.isEmpty()) {
            return;
        }

        String tag = element.getTagName();
        String type;
        if ("item".equals(tag)) {
            type = element.getAttribute("type");
            if (type == null || type.isEmpty()) {
                return;
            }
        } else if ("declare-styleable".equals(tag)) {
            type = "styleable";
        } else {
            type = tag;
        }

        // Normalize name: dots are treated as underscores in resource names
        String normalizedName = name.replace('.', '_');
        String folder = context.file.getParent();
        String key = (folder != null ? folder : "") + "/" + type + "/" + normalizedName;

        Location location = context.getLocation(element.getAttributeNode("name"));
        Location existing = mResources.put(key, location);
        if (existing != null) {
            location.setSecondary(existing);
            context.report(ISSUE, location, String.format("Duplicate resource name \"%s\"", name));
        }
    }
}