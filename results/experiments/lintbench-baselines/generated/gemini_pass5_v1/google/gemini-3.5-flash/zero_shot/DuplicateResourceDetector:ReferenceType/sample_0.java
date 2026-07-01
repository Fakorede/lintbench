package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class DuplicateResourceDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ReferenceType",
            "Incorrect reference types",
            "When you generate a resource alias, the resource you are pointing to must be of the same type as the alias",
            Category.CORRECTNESS,
            8,
            Severity.FATAL,
            new Implementation(
                    DuplicateResourceDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("item", "string", "dimen", "color", "drawable", "bool", "integer", "layout");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Node parentNode = element.getParentNode();
        if (parentNode == null || parentNode.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }
        Element parent = (Element) parentNode;
        if (!parent.getTagName().equals("resources")) {
            return;
        }

        String tagName = element.getTagName();
        String aliasType;
        if (tagName.equals("item")) {
            if (!element.hasAttribute("type")) {
                return;
            }
            aliasType = element.getAttribute("type");
        } else {
            aliasType = tagName;
        }

        String value = element.getTextContent().trim();
        if (value.startsWith("@") && !value.startsWith("@null") && !value.startsWith("@+")) {
            int slash = value.indexOf('/');
            if (slash != -1) {
                int typeStart = value.indexOf(':');
                if (typeStart == -1 || typeStart > slash) {
                    typeStart = 1; // right after '@'
                } else {
                    typeStart++; // right after ':'
                }
                String targetType = value.substring(typeStart, slash);
                if (!aliasType.equals(targetType)) {
                    String message = String.format(
                            "Mismatch: alias is of type `%s` but points to `%s` of type `%s`",
                            aliasType, value, targetType
                    );
                    context.report(ISSUE, element, context.getLocation(element), message);
                }
            }
        }
    }
}