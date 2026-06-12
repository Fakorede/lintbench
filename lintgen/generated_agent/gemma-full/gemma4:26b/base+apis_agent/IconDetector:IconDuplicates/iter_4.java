package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class IconDetector extends com.android.tools.lint.detector.api.Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "DuplicateIcon",
            "This icon is a duplicate of another icon in your resources.",
            com.android.tools.lint.detector.api.Issue.Severity.WARNING,
            null,
            null,
            true
    );

    private static final Map<String, String> seenIcons = new HashMap<>();

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("vector");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tagName = element.getLocalName();
        if ("vector".equals(tagName) || "animated-vector".equals(tagName)) {
            String signature = generateSignature(element);
            if (signature.isEmpty()) {
                return;
            }

            // We use a placeholder since we cannot reliably get the current file's URI 
            // from XmlContext in all environments without causing compilation errors.
            String originalName = seenIcons.putIfAbsent(signature, "unknown");

            if (originalName != null) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "Duplicate icon found."
                );
            }
        }
    }

    private String generateSignature(Node node) {
        if (node == null) {
            return "";
        }
        if (node.getNodeType() == Node.TEXT_NODE) {
            String text = node.getTextContent().trim();
            return text.isEmpty() ? "" : text;
        }

        StringBuilder sb = new StringBuilder();
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            sb.append("<").append(node.getNodeName());
            NamedNodeMap attrs = node.getAttributes();
            if (attrs != null) {
                for (int i = 0; i < attrs.getLength(); i++) {
                    Attr attr = (Attr) attrs.item(i);
                    sb.append(" ").append(attr.getName()).append("='").append(attr.getValue()).append("'");
                }
            }
            sb.append(">");
        }

        Node child = node.getFirstChild();
        while (child != null) {
            String childSignature = generateSignature(child);
            if (!childSignature.isEmpty()) {
                sb.append(childSignature);
            }
            child = child.getNextSibling();
        }

        if (node.getNodeType() == Node.ELEMENT_NODE) {
            sb.append("</").append(node.getNodeName()).append(">");
        }
        return sb.toString();
    }
}