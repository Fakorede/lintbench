package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.io.File;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class DuplicateResourceDetector extends Detector implements XmlScanner, BinaryResourceScanner {

    public static final Issue ISSUE = Issue.create(
            "DuplicateDefinition",
            "Duplicate definitions of resources",
            "You can define a resource multiple times in different resource folders; " +
            "that's how string translations are done, for example. However, defining " +
            "the same resource more than once in the same resource folder is likely " +
            "an error, for example attempting to add a new resource without realizing " +
            "that the name is already used, and so on.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    DuplicateResourceDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE, Scope.BINARY_RESOURCE_FILE)
            )
    );

    private final Map<String, Location> mDeclaredResources = new HashMap<>();

    @Override
    public void beforeCheckEachProject(Context context) {
        mDeclaredResources.clear();
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        File file = context.file;
        File parentFile = file.getParentFile();
        if (parentFile == null) {
            return;
        }
        String folderName = parentFile.getName();
        ResourceFolderType folderType = ResourceFolderType.getFolderType(folderName);
        if (folderType == null) {
            return;
        }

        if (folderType == ResourceFolderType.VALUES) {
            Element root = document.getDocumentElement();
            if (root == null || !root.getTagName().equals("resources")) {
                return;
            }
            NodeList children = root.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) child;
                    String name = element.getAttribute("name");
                    if (name == null || name.isEmpty()) {
                        continue;
                    }
                    String type = element.getTagName();
                    if (type.equals("item")) {
                        String typeAttr = element.getAttribute("type");
                        if (typeAttr != null && !typeAttr.isEmpty()) {
                            type = typeAttr;
                        }
                    }
                    if (type.equals("public")) {
                        continue;
                    }
                    if (type.equals("declare-styleable")) {
                        type = "styleable";
                    }
                    if (type.equals("string-array") || type.equals("integer-array")) {
                        type = "array";
                    }

                    Location location = context.getNameLocation(element);
                    recordResource(context, location, parentFile.getPath(), type, name);
                }
            }
        } else {
            String type = folderType.getName();
            String fileName = file.getName();
            int dot = fileName.indexOf('.');
            String name = dot != -1 ? fileName.substring(0, dot) : fileName;

            Location location = context.getLocation(document.getDocumentElement() != null ? document.getDocumentElement() : document);
            recordResource(context, location, parentFile.getPath(), type, name);
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return true;
    }

    @Override
    public void checkBinaryResource(ResourceContext context) {
        File file = context.file;
        File parentFile = file.getParentFile();
        if (parentFile == null) {
            return;
        }
        String folderName = parentFile.getName();
        ResourceFolderType folderType = ResourceFolderType.getFolderType(folderName);
        if (folderType == null) {
            return;
        }

        String type = folderType.getName();
        String fileName = file.getName();
        int dot = fileName.indexOf('.');
        String name = dot != -1 ? fileName.substring(0, dot) : fileName;

        Location location = Location.create(file);
        recordResource(context, location, parentFile.getPath(), type, name);
    }

    private void recordResource(Context context, Location currentLocation, String parentPath, String type, String name) {
        String key = parentPath + ":" + type + ":" + name;
        if (mDeclaredResources.containsKey(key)) {
            Location originalLocation = mDeclaredResources.get(key);
            String message = String.format("Resource `%s` of type `%s` has already been defined in this folder", name, type);
            if (originalLocation != null) {
                currentLocation.setSecondary(originalLocation);
                originalLocation.setMessage("First definition here");
            }
            context.report(ISSUE, currentLocation, message);
        } else {
            mDeclaredResources.put(key, currentLocation);
        }
    }
}