package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class DuplicateResourceDetector extends Detector implements XmlScanner {
    public static final Issue ISSUE = Issue.create(
        "DuplicateDefinition",
        "Duplicate definitions of resources",
        "You can define a resource multiple times in different resource folders; that's how string translations are done, for example. However, defining the same resource more than once in the same resource folder is likely an error, for example attempting to add a new resource without realizing that the name is already used, and so on.",
        Category.CORRECTNESS,
        6,
        Severity.ERROR,
        new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private Map<String, Map<String, Location>> mFolderNames;

    @Override
    public void beforeCheckEachProject(Context context) {
        mFolderNames = new HashMap<>();
    }

    @Override
    public void afterCheckEachProject(Context context) {
        mFolderNames = null;
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (mFolderNames == null) {
            mFolderNames = new HashMap<>();
        }

        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != null && folderType != ResourceFolderType.VALUES) {
            return;
        }
        if (folderType == null) {
            File parent = context.file.getParentFile();
            if (parent == null || !parent.getName().startsWith("values")) {
                return;
            }
        }

        String name = element.getAttribute("name");
        if (name == null || name.isEmpty()) {
            return;
        }

        Node parent = element.getParentNode();
        if (parent == null || parent.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }
        String parentTag = parent.getNodeName();

        String lookupKey;
        if ("resources".equals(parentTag)) {
            lookupKey = name.replace('.', '_');
        } else if ("style".equals(parentTag)) {
            String styleName = ((Element) parent).getAttribute("name");
            if (styleName == null || styleName.isEmpty()) {
                return;
            }
            lookupKey = "style:" + styleName.replace('.', '_') + "/" + name.replace('.', '_');
        } else {
            return;
        }

        File parentFile = context.file.getParentFile();
        if (parentFile == null) {
            return;
        }
        String folderPath = parentFile.getPath();

        Map<String, Location> names = mFolderNames.computeIfAbsent(folderPath, f -> new HashMap<>());
        Location existing = names.get(lookupKey);
        if (existing != null) {
            Location location = context.getLocation(element);
            location.setSecondary(existing);
            context.report(ISSUE, location, "Duplicate resource name \"" + name + "\"");
        } else {
            names.put(lookupKey, context.getLocation(element));
        }
    }
}