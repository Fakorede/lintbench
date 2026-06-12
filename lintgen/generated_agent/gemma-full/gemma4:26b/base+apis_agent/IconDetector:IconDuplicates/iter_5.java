package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Issue.Implementation;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class IconDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "DuplicateIcon",
            "This icon is a duplicate of another icon in your resources.",
            "If an icon is repeated under different names, you can consolidate and just use one of the icons to make your application smaller.",
            new Implementation(
                    IconDetector.class,
                    null
            ),
            Severity.WARNING
    );

    /**
     * We use a static set to track signatures across different files during a single scan.
     * Note: In a multi-module project, this state might persist between module scans 
     * if the same Lint daemon is used, which is a known limitation for cross-file checks 
     * in custom detectors without a global registry.
     */
    private static final Set<String> seenSignatures = new HashSet<>();

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("vector", "animated-vector");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String signature = generateSignature(element);
        if (signature.isEmpty()) {
            return;
        }

        if (seenSignatures.contains(signature)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "This icon is a duplicate of another icon in your resources."
            );
        } else {
            seenSignatures.add(signature);
        }
    }

    /**
     * Generates a unique string representation of the XML element and its children.
     */
    private String generateSignature(Node node) {
        if (node == null) {
            return "";
        }

        if (node.getNodeType() == Node.TEXT_NODE) {
            String text = node.getTextContent().trim();
            return text.isEmpty() ? "" : text;
        }

        if (node.getNodeType() != Node.ELEMENT_NODE) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<").append(node.getNodeName());

        org.w3c.dom.NamedNodeMap attrs = node.getAttributes();
        if (attrs != null) {
            for (int i = 0; i < attrs.getLength(); i++) {
                Attr attr = (Attr) attrs.item(i);
                sb.append(" ").append(attr.getName()).append("='").append(attr.getValue()).append("'");
            }
        }
        sb.append(">");

        Node child = node.getFirstChild();
        while (child != null) {
            String childSignature = generateSignature(child);
            if (!childSignature.isEmpty()) {
                sb.append(childSignature);
            }
            child = child.getNextSibling();
        }

        sb.append("</").append(node.getNodeName()).append(">");
        return sb.toString();
    }
}