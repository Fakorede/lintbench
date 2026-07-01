package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class DuplicateResourceDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ReferenceType",
                    "Incorrect reference types",
                    "When you generate a resource alias, the resource you are pointing to must be of the same type as the alias.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("type");
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        // No state initialization required
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Element element = attribute.getOwnerElement();
        if (element == null || !"item".equals(element.getTagName())) {
            return;
        }

        String expectedType = attribute.getValue();
        if (expectedType == null) {
            return;
        }
        expectedType = expectedType.trim();

        String textContent = element.getTextContent();
        if (textContent == null) {
            return;
        }
        textContent = textContent.trim();

        String actualType = getReferenceType(textContent);
        if (actualType != null && !actualType.equals(expectedType)) {
            String message = String.format(
                    "Resource alias type mismatch: expected '%s' but referenced '%s'",
                    expectedType, actualType);
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }

    private static String getReferenceType(@NonNull String reference) {
        if (reference.isEmpty()) {
            return null;
        }
        int start = 0;
        char first = reference.charAt(0);
        if (first == '@' || first == '?') {
            start = 1;
        } else {
            return null;
        }
        if (start < reference.length() && (reference.charAt(start) == '*' || reference.charAt(start) == '+')) {
            start++;
        }
        int colon = reference.indexOf(':', start);
        int slash = reference.indexOf('/', start);
        if (slash == -1) {
            return null;
        }
        if (colon != -1 && colon < slash) {
            start = colon + 1;
        }
        return reference.substring(start, slash);
    }
}