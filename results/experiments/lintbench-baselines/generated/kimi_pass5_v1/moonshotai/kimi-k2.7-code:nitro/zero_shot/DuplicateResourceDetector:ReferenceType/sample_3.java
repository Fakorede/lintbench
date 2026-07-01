package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScannerConstants;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class DuplicateResourceDetector extends ResourceXmlDetector {

    public static final Issue REFERENCE_TYPE = Issue.create(
            "ReferenceType",
            "Incorrect reference types",
            "When you generate a resource alias, the resource you are pointing to must be "
                    + "of the same type as the alias.",
            Category.CORRECTNESS,
            7,
            Severity.ERROR,
            new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final Pattern RESOURCE_REF_PATTERN = Pattern.compile(
            "^@(?:\\*?[A-Za-z0-9_.]+:)?([A-Za-z0-9_]+)/([A-Za-z0-9_.]+)$");

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Node parent = element.getParentNode();
        if (parent == null || !"resources".equals(parent.getNodeName())) {
            return;
        }

        String declaredType = element.getTagName();
        if ("item".equals(declaredType)) {
            if (!element.hasAttribute("type")) {
                return;
            }
            declaredType = element.getAttribute("type");
        }

        String text = element.getTextContent();
        if (text == null) {
            return;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return;
        }

        Matcher matcher = RESOURCE_REF_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            return;
        }

        String referencedType = matcher.group(1);
        if (!referencedType.equals(declaredType)) {
            String message = String.format(
                    "Resource alias `%1$s` is declared as type `%2$s` but points to a `%3$s` resource (`%4$s`)",
                    element.getAttribute("name"), declaredType, referencedType, trimmed);
            context.report(REFERENCE_TYPE, element, context.getLocation(element), message);
        }
    }
}