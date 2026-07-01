package com.android.tools.lint.checks;

import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.lint.detector.api.*;
import org.w3c.dom.*;

import java.io.File;
import java.net.URI;
import java.util.*;

import static com.android.SdkConstants.*;

public class DuplicateIdDetector extends LayoutDetector {
    public static final Issue ISSUE = Issue.create(
        "DuplicateIncludedIds",
        "Duplicate ids across layouts combined with include tags",
        "It's okay for two independent layouts to use the same ids. However, if layouts are combined with include tags, then the id's need to be unique within any chain of included layouts, or `Activity#findViewById()` can return an unexpected view.",
        Category.CORRECTNESS, 6, Severity.WARNING,
        new Implementation(DuplicateIdDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (element != context.document.getDocumentElement()) {
            return;
        }
        if (context.getResourceType() != ResourceType.LAYOUT) {
            return;
        }

        Map<String, Attr> ids = new HashMap<>();
        Set<File> visited = new HashSet<>();
        Set<String> reported = new HashSet<>();
        collectAndCheck(context, context.file, context.document, visited, ids, reported);
    }

    private void collectAndCheck(XmlContext context, File file, Document doc, Set<File> visited, Map<String, Attr> ids, Set<String> reported) {
        if (!visited.add(file)) return;
        Element root = doc.getDocumentElement();
        if (root == null) return;

        List<Element> includes = new ArrayList<>();
        traverse(root, ids, includes, reported, context, file);

        for (Element include : includes) {
            String layoutAttr = include.getAttribute("layout");
            if (layoutAttr == null || layoutAttr.isEmpty()) continue;
            ResourceUrl url = ResourceUrl.parse(layoutAttr);
            if (url == null || url.type != ResourceType.LAYOUT) continue;

            File includedFile = context.getProject().findResourceFile(ResourceType.LAYOUT, url.name);
            if (includedFile == null) continue;

            try {
                Document includedDoc = XmlParser.parse(includedFile);
                collectAndCheck(context, includedFile, includedDoc, visited, ids, reported);
            } catch (Exception e) {
                // Ignore parse errors for included layouts
            }
        }
    }

    private void traverse(Element element, Map<String, Attr> ids, List<Element> includes, Set<String> reported, XmlContext context, File currentFile) {
        String idAttrVal = element.getAttributeNS(ANDROID_URI, ATTR_ID);
        if (idAttrVal != null && !idAttrVal.isEmpty()) {
            String idName = extractIdName(idAttrVal);
            if (idName != null) {
                Attr attr = element.getAttributeNodeNS(ANDROID_URI, ATTR_ID);
                if (ids.containsKey(idName)) {
                    if (!reported.contains(idName)) {
                        reported.add(idName);
                        Attr existing = ids.get(idName);
                        if (isInFile(attr, context.file) || isInFile(existing, context.file)) {
                            Location location = context.getLocation(attr);
                            try {
                                Location secondary = context.getLocation(existing);
                                location.setSecondary(secondary);
                            } catch (Exception ignored) {
                                // Fallback if secondary location resolution fails
                            }
                            context.report(ISSUE, attr, location, String.format("Duplicate id `%1$s`, already defined in this layout chain", idName));
                        }
                    }
                } else {
                    ids.put(idName, attr);
                }
            }
        }

        if (TAG_INCLUDE.equals(element.getTagName())) {
            includes.add(element);
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                traverse((Element) child, ids, includes, reported, context, currentFile);
            }
        }
    }

    private boolean isInFile(Attr attr, File targetFile) {
        Document doc = attr.getOwnerDocument();
        if (doc == null) return false;
        String uri = doc.getDocumentURI();
        if (uri == null) return false;
        try {
            File attrFile = new File(new URI(uri));
            return attrFile.equals(targetFile);
        } catch (Exception e) {
            return false;
        }
    }

    private String extractIdName(String id) {
        int slash = id.indexOf('/');
        return slash != -1 ? id.substring(slash + 1) : id;
    }
}