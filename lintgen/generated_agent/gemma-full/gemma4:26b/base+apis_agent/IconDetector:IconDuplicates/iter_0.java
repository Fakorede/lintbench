package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class IconDetector extends Detector implements XmlScanner {

    private static final Issue ISSUE = Issue.create(
            "DuplicateIcon",
            "This icon is a duplicate of another icon in your resources.",
            Issue.Level.WARNING,
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
    public Collection<String> getApplicableElements() {
        return Arrays.asList("vector", "selector", "layer-list", "shape", "bitmap", "inset", "clip", "ripple");
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String signature = generateSignature(element);
        if (signature == null) {
            return;
        }

        int hash = signature.hashCode();
        
        // Attempt to extract the resource name from the document URI.
        // In Lint's XML scanning, the baseURI typically points to the file path.
        String uri = element.getOwnerDocument().getBaseURI();
        String currentName = extractResourceName(uri);

        // If we cannot determine a specific name (e.g., URI is empty), use a placeholder.
        if (currentName == null) {
            currentName = "unknown_resource";
        }

        // putIfAbsent returns the previous value associated with the key, or null if there was no mapping.
        String originalName = seenIcons.putIfAbsent(hash, currentName);

        // If an original name exists and it's different from the current one, we found a duplicate.
        if (originalName != null && !originalName.equals(currentName)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(),
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
    private String generateSignature(Element element) {
        StringBuilder sb = new StringBuilder();
        sb.append(element.getTagName());

        // Append all attributes and their values to the signature.
        for (int i = 0; i < element.getAttributes().getLength(); i++) {
            Attr attr = element.getAttributes().item(i);
            sb.append("|").append(attr.getName()).append("=").append(attr.getValue());
        }

        // Append child node information (text and tag names) to the signature.
        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.TEXT_NODE) {
                String text = child.getTextContent().trim();
                if (!text.isEmpty()) {
                    sb.append("|").append(text);
                }
            } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                sb.append("|").append(child.getNodeName());
            }
            child = child.getNextSibling();
        }

        return sb.toString();
    }
}