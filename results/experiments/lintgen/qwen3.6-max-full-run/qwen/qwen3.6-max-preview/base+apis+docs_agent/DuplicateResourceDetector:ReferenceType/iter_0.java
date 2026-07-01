package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;

public class DuplicateResourceDetector extends Detector implements XmlScanner {
    public static final Issue ISSUE = Issue.create(
        "ReferenceType",
        "Incorrect reference types",
        "When you generate a resource alias, the resource you are pointing to must be of the same type as the alias",
        Category.CORRECTNESS,
        6,
        Severity.ERROR,
        new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("item");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String type = element.getAttribute("type");
        if (type == null || type.isEmpty()) {
            return;
        }

        String reference = element.getTextContent();
        if (reference == null) {
            return;
        }
        reference = reference.trim();
        if (!reference.startsWith("@")) {
            return;
        }

        String refType = extractResourceType(reference);
        if (refType != null && !type.equals(refType)) {
            context.report(ISSUE, element, context.getLocation(element),
                "Resource alias type mismatch: expected `" + type + "` but referenced `" + refType + "`");
        }
    }

    private static String extractResourceType(String reference) {
        int i = 0;
        int len = reference.length();
        if (i < len && reference.charAt(i) == '@') i++;
        if (i < len && reference.charAt(i) == '+') i++;
        if (i < len && reference.charAt(i) == '*') i++;

        String ref = reference.substring(i);
        int colon = ref.indexOf(':');
        if (colon != -1) {
            ref = ref.substring(colon + 1);
        }
        int slash = ref.indexOf('/');
        if (slash != -1) {
            return ref.substring(0, slash);
        }
        return null;
    }
}