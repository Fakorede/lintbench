package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
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
                    "Resource aliases must reference a resource of the same type. For example, "
                            + "<item type=\"drawable\" name=\"alias\">@drawable/real</item> is valid, "
                            + "but referencing a different type such as @string/foo or @layout/main "
                            + "from a drawable alias is not.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.emptyList();
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null; // visit every element and look for resource aliases
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        // No per-file setup required.
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // Reference-type checks apply to element text, not attributes.
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String aliasType = getAliasType(element);
        if (aliasType == null) {
            return;
        }

        String text = element.getTextContent();
        if (text == null) {
            return;
        }

        String referenceType = getReferenceType(text.trim());
        if (referenceType == null) {
            return;
        }

        if (!aliasType.equals(referenceType)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    String.format(
                            "Expected reference type '%1$s' for this resource alias, but found '%2$s'",
                            aliasType,
                            referenceType));
        }
    }

    private static String getAliasType(@NonNull Element element) {
        String tag = element.getTagName();
        if ("item".equals(tag)) {
            String type = element.getAttribute("type");
            if (type != null && !type.isEmpty()) {
                return type;
            }
            return null;
        }
        return tag;
    }

    private static String getReferenceType(@NonNull String value) {
        if (value.isEmpty()) {
            return null;
        }

        String s = value;
        if (s.startsWith("@")) {
            s = s.substring(1);
            if (s.startsWith("+")) {
                s = s.substring(1);
            }
        } else if (s.startsWith("?")) {
            s = s.substring(1);
        } else {
            return null;
        }

        int slash = s.indexOf('/');
        if (slash == -1 || slash == s.length() - 1) {
            return null;
        }

        // Make sure the value is a single reference, not a string that contains a reference.
        int end = slash + 1;
        while (end < s.length() && !Character.isWhitespace(s.charAt(end))) {
            end++;
        }
        if (end < s.length() && !s.substring(end).trim().isEmpty()) {
            return null;
        }

        String beforeSlash = s.substring(0, slash);
        int colon = beforeSlash.indexOf(':');
        if (colon != -1) {
            return beforeSlash.substring(colon + 1);
        }
        return beforeSlash;
    }
}