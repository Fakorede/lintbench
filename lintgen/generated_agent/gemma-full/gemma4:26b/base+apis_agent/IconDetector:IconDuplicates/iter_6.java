package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
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
            new Issue.Implementation(IconDetector.class, null),
            Severity.WARNING
    );

    /**
     * We use a static set to track signatures across different files during a single scan.
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
     * Generates a unique string representation of the XML element and its children to identify duplicates.
     */
    private String generateSignature(Node node) {
        if (node == null || node.getNodeType() != Node.ELEMENT_NODE) {
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
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                String childSig = generateSignature(child);
                if (!childSig.isEmpty()) {
                    sb.append(childSig);
                }
            } else if (child.getNodeType() == Node.TEXT_NODE) {
                String text = child.getTextContent().trim();
                if (!text.isEmpty()) {
                    sb.append(text);
                }
            }
            child = child.getNextSibling();
        }

        sb.append("</").append(node.getNodeName()).append(">");
        return sb.toString();
    }
}