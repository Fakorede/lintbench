package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Issue.Implementation;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class IconDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "DuplicateIcon",
            "This icon is a duplicate of another icon in your resources.",
            "If an icon is repeated under different names, you can consolidate and just use one of the icons to make your application smaller.",
            new Implementation(IconDetector.class, null),
            Severity.WARNING
    );

    private static final Set<String> seenSignatures = ConcurrentHashMap.newKeySet();

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        Element root = document.getDocumentElement();
        if (root != null) {
            String signature = generateSignature(root);
            if (!signature.isEmpty() && !seenSignatures.add(signature)) {
                context.report(
                        ISSUE,
                        root,
                        context.getLocation(root),
                        "This icon is a duplicate of another icon in your resources."
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

        if (node.getNodeType() != Node.ELEMENT_NODE) {
            return "";
        }

        Element element = (Element) node;
        StringBuilder sb = new StringBuilder();
        sb.append("<").append(element.getTagName());

        List<Attr> attrs = new ArrayList<>();
        for (int i = 0; i < element.getAttributes().getLength(); i++) {
            attrs.add((Attr) element.getAttributes().item(i));
        }
        Collections.sort(attrs, Comparator.comparing(Attr::getName));

        for (Attr attr : attrs) {
            sb.append(" ").append(attr.getName()).append("=\"").append(attr.getValue()).append("\"");
        }
        sb.append(">");

        Node child = element.getFirstChild();
        while (child != null) {
            String childSig = generateSignature(child);
            if (!childSig.isEmpty()) {
                sb.append(childSig);
            }
            child = child.getNextSibling();
        }

        sb.append("</").append(element.getTagName()).append(">");
        return sb.toString();
    }
}