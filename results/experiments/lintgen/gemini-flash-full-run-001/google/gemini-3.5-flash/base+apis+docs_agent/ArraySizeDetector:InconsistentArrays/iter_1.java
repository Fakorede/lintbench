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
import com.android.tools.lint.detector.api.XmlParser;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ArraySizeDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "InconsistentArrays",
            "Inconsistencies in array element counts",
            "When an array is translated in a different locale, it should normally have " +
            "the same number of elements as the original array. When adding or removing " +
            "elements to an array, it is easy to forget to update all the locales, and this " +
            "lint warning finds inconsistencies like these.\n\n" +
            "Note however that there may be cases where you really want to declare a " +
            "different number of array items in each configuration (for example where " +
            "the array represents available options, and those options differ for " +
            "different layout orientations and so on), so use your own judgment to " +
            "decide if this is really an error.\n\n" +
            "You can suppress this error type if it finds false errors in your project.",
            Category.CORRECTNESS,
            7,
            Severity.WARNING,
            new Implementation(ArraySizeDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static class ArrayDeclaration {
        final String name;
        final int count;
        final String folder;
        final Location location;

        ArrayDeclaration(String name, int count, String folder, Location location) {
            this.name = name;
            this.count = count;
            this.folder = folder;
            this.location = location;
        }
    }

    private final Map<String, List<ArrayDeclaration>> arrays = new HashMap<>();
    private final Set<File> visitedFiles = new HashSet<>();

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        List<String> elements = new ArrayList<>();
        elements.add("string-array");
        elements.add("integer-array");
        elements.add("array");
        return elements;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.VALUES) {
            return;
        }
        String name = element.getAttribute("name");
        if (name == null || name.isEmpty()) {
            return;
        }

        int count = 0;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "item".equals(child.getNodeName())) {
                count++;
            }
        }

        File parentFile = context.file.getParentFile();
        String folderName = parentFile != null ? parentFile.getName() : "values";
        Location location = context.getLocation(element);

        synchronized (arrays) {
            visitedFiles.add(context.file);
            List<ArrayDeclaration> list = arrays.computeIfAbsent(name, k -> new ArrayList<>());
            list.add(new ArrayDeclaration(name, count, folderName, location));
        }
    }

    @Override
    public void afterCheckEachProject(Context context) {
        // Find all other files in values* directories to support incremental analysis
        for (File resFolder : context.getProject().getResourceFolders()) {
            File[] subdirs = resFolder.listFiles();
            if (subdirs != null) {
                for (File subdir : subdirs) {
                    if (subdir.isDirectory() && subdir.getName().startsWith("values")) {
                        File[] files = subdir.listFiles();
                        if (files != null) {
                            for (File file : files) {
                                if (file.isFile() && file.getName().endsWith(".xml")) {
                                    boolean alreadyVisited;
                                    synchronized (arrays) {
                                        alreadyVisited = visitedFiles.contains(file);
                                    }
                                    if (!alreadyVisited) {
                                        try {
                                            Document doc = context.getClient().getXmlParser().parseXml(file);
                                            if (doc != null) {
                                                processDocument(doc, file, context);
                                            }
                                        } catch (Exception e) {
                                            // Ignore parse errors of other files
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        synchronized (arrays) {
            for (Map.Entry<String, List<ArrayDeclaration>> entry : arrays.entrySet()) {
                String arrayName = entry.getKey();
                List<ArrayDeclaration> declarations = entry.getValue();
                if (declarations.size() <= 1) {
                    continue;
                }

                ArrayDeclaration defaultDecl = null;
                for (ArrayDeclaration decl : declarations) {
                    if ("values".equals(decl.folder)) {
                        defaultDecl = decl;
                        break;
                    }
                }

                if (defaultDecl != null) {
                    int defaultCount = defaultDecl.count;
                    for (ArrayDeclaration decl : declarations) {
                        if (decl != defaultDecl && decl.count != defaultCount) {
                            String message = String.format(
                                    "Array %s has an inconsistent number of items (%d) compared to the default configuration in %s (%d)",
                                    arrayName, decl.count, defaultDecl.folder, defaultCount
                            );
                            context.report(ISSUE, decl.location, message);
                        }
                    }
                } else {
                    ArrayDeclaration firstDecl = declarations.get(0);
                    int firstCount = firstDecl.count;
                    for (int i = 1; i < declarations.size(); i++) {
                        ArrayDeclaration decl = declarations.get(i);
                        if (decl.count != firstCount) {
                            String message = String.format(
                                    "Array %s has an inconsistent number of items (%d) compared to configuration in %s (%d)",
                                    arrayName, decl.count, firstDecl.folder, firstCount
                            );
                            context.report(ISSUE, decl.location, message);
                        }
                    }
                }
            }
            arrays.clear();
            visitedFiles.clear();
        }
    }

    private void processDocument(Document doc, File file, Context context) {
        if (doc == null || doc.getDocumentElement() == null) {
            return;
        }
        Element root = doc.getDocumentElement();
        NodeList resources = root.getChildNodes();
        String folderName = file.getParentFile().getName();
        for (int i = 0; i < resources.getLength(); i++) {
            Node node = resources.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                String tagName = element.getTagName();
                if ("string-array".equals(tagName) || "integer-array".equals(tagName) || "array".equals(tagName)) {
                    String name = element.getAttribute("name");
                    if (name != null && !name.isEmpty()) {
                        int count = 0;
                        NodeList items = element.getChildNodes();
                        for (int j = 0; j < items.getLength(); j++) {
                            Node item = items.item(j);
                            if (item.getNodeType() == Node.ELEMENT_NODE && "item".equals(item.getNodeName())) {
                                count++;
                            }
                        }
                        Location location = context.getClient().getXmlParser().getLocation(file, element);
                        synchronized (arrays) {
                            List<ArrayDeclaration> list = arrays.computeIfAbsent(name, k -> new ArrayList<>());
                            list.add(new ArrayDeclaration(name, count, folderName, location));
                        }
                    }
                }
            }
        }
    }
}