package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class IconDetector extends com.android.tools.lint.detector.api.Detector implements XmlScanner {

    private static final Issue ISSUE = Issue.create(
            "DuplicateIcon",
            "This icon is a duplicate of another icon in your resources.",
            com.android.tools.lint.detector.api.Issue.Severity.WARNING,
            null,
            null,
            true
    );

    private static final ConcurrentHashMap<Integer, String> seenIcons = new ConcurrentHashMap<>();

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
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
            currentName = "unknown";
        }

        String originalName = seenIcons.putIfAbsent(hash, currentName);

        if (originalName != null && !originalName.equals(currentName)) {
            context.report(
                    ISSUE,
                    root,
                    context.getLocation(root),
                    "Duplicate icon found. This is a duplicate of @" + originalName
            );
        }
    }

    private String extractResourceName(String uri) {
        if (uri == null || uri.isEmpty()) {
            return null;
        }
        try {
            int lastSlash = uri.lastIndexOf('/');
            String filename = (lastSlash != -1) ? uri.substring(lastSlash + 1) : uri;
            int dotIndex = filename.lastIndexOf('.');
            return (dotIndex != -1) ? filename.substring(0, dotIndex) : filename;
        } catch (Exception e) {
            return null;
        }
    }

    private String generateSignature(Node node) {
        StringBuilder sb = new StringBuilder();
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            sb.append("<").append(node.getNodeName());

            var attrs = node.getAttributes();
            for (int i = 0; i < attrs.getLength(); i++) {
                Attr attr = (Attr) attrs.item(i);
                sb.append(" ").append(attr.getName()).append("='").append(attr.getValue()).append("'");
            }
            sb.append(">");
        }

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