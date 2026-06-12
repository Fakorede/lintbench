package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.concurrent.ConcurrentHashMap;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class IconDetector extends Detector implements XmlScanner {

    private static final Issue ISSUE = Issue.create(
            "DuplicateIcon",
            "This icon is a duplicate of another icon in your resources.",
            Issue.Severity.WARNING,
            null,
            null,
            true
    );

    /**
     * A thread-safe map to track seen icon signatures and their original resource names.
     * Key: Hash of the XML element's structural signature.
     * Value: The name of the first resource found with this signature.
     */
    private static final ConcurrentHashMap<Integer, String> seenIcons = new ConcurrentHashMap<>();

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        String signature = generateSignature(root);
        int hash = signature.hashCode();

        String uri = document.getBaseURI();
        String currentName = extractResourceName(uri);

        if (currentName == null) {
            currentName = "unknown_resource";
        }

        // putIfAbsent returns the previous value associated with the key, or null if there was no mapping.
        String originalName = seenIcons.putIfAbsent(hash, currentName);

        // If an original name exists and it's different from the current one, we found a duplicate.
        if (originalName != null && !originalName.equals(currentName)) {
            context.report(
                    ISSUE,
                    root,
                    context.getLocation(root),
                    "Duplicate icon found. This is a duplicate of @%s/%s",
                    originalName,
                    originalName
            );
        }
    }

    /**
     * Extracts the resource name (filename without extension) from a URI/path.
     */
    private String extractResourceName(String uri) {
        if (uri == null || uri.isEmpty()) {
            return null;
        }
        try {
            String filename = uri.substring(uri.lastIndexOf('/') + 1);
            int dotIndex = filename.lastIndexOf('.');
            return (dotIndex != -1) ? filename.substring(0, dotIndex) : filename;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Generates a structural signature for the XML element based on its tag name,
     * attributes, and text content to identify identical drawable definitions.
     */
    private String generateSignature(Node node) {
        StringBuilder sb = new StringBuilder();
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            sb.append("<").append(node.getNodeName());

            // Append all attributes and their values to the signature.
            var attrs = node.getAttributes();
            for (int i = 0; i < attrs.getLength(); i++) {
                Attr attr = (Attr) attrs.item(i);
                sb.append(" ").append(attr.getName()).append("='").append(attr.getValue()).append("'");
            }
            sb.append(">");
        }

        // Append child node information (text and tag names) to the signature.
        Node child = node.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.TEXT_NODE) {
                String text = child.getTextContent().trim();
                if (!text.isEmpty()) {
                    sb.append(text);
                }
            } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                sb.append(generateSignature(child));
            }
            child = child.getNextSibling();
        }

        if (node.getNodeType() == Node.ELEMENT_NODE) {
            sb.append("</").append(node.getNodeName()).append(">");
        }

        return sb.toString();
    }
}