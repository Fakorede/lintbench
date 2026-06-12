package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Issue.Implementation;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.w3c.dom.Attr;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class IconDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "DuplicateIcon",
            "This icon is a duplicate of another icon in your resources.",
            "If an icon is repeated under different names, you can consolidate and just use one of the icons to make your application smaller.",
            new Implementation(IconDetector.class, null),
            Severity.WARNING
    );

    /**
     * A thread-safe set to track signatures across different files during a single scan.
     */
    private static final Set<String> seenSignatures = ConcurrentHashMap.newKeySet();

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folder    == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
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

        if (!seenSignatures.add(signature)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "This icon is a duplicate of another icon in your resources."
            );
        }
    }

    /**
     * Generates a stable string representation of the XML element and its children to identify duplicates.
     * It sorts attributes by name to ensure that different attribute orders don't result in different signatures.
     */
    private String generateSignature(Node node) {
        if (node == null || node.getNodeType() != Node.ELEMENT_NODE) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<").append(node.getNodeName());

        NamedNodeMap attrs = node.getAttributes();
        if (attrs != null && attrs.getLength() > 0) {
            List<Attr> attrList = new ArrayList<>();
            for (int i = 0; i < attrs.getLength(); i++) {
                attrList.add((Attr) attrs.item(i));
            }
            // Sort attributes by name to ensure stability
            Collections.sort(attrList, Comparator.comparing(Attr::getName));
            for (Attr attr : attrList) {
                sb.append(" ").append(attr.getName()).append("=\"").append(attr.getValue()).append("\"");
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