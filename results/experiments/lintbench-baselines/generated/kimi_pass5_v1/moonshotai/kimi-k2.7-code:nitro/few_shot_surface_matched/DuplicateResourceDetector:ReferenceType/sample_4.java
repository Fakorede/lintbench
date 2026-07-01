package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceFolderType;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class DuplicateResourceDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ReferenceType",
                    "Incorrect reference type",
                    "When you generate a resource alias, the resource you are pointing to must be"
                            + " of the same type as the alias.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    new Implementation(
                            DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("name");
    }

    @Override
    public void beforeCheckFile(Context context) {
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }

        String aliasType = getAliasType(element);
        if (aliasType == null || aliasType.isEmpty()) {
            return;
        }

        String value = element.getTextContent();
        if (value == null) {
            return;
        }
        value = value.trim();
        if (!value.startsWith("@")) {
            return;
        }

        String refType = getReferenceType(value);
        if (refType == null || refType.isEmpty()) {
            return;
        }

        if (!aliasType.equals(refType)) {
            String message =
                    String.format(
                            "The resource %1$s is of type %2$s and cannot be aliased as a %3$s.",
                            value, refType, aliasType);
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }

    private static String getAliasType(Element element) {
        String tag = element.getTagName();
        if ("item".equals(tag)) {
            String type = element.getAttribute("type");
            if (type != null && !type.isEmpty()) {
                return type;
            }
            return null;
        }
        if ("string-array".equals(tag) || "integer-array".equals(tag)) {
            return "array";
        }
        return tag;
    }

    private static String getReferenceType(String value) {
        String ref = value.substring(1);
        int i = 0;
        int len = ref.length();
        while (i < len && (ref.charAt(i) == '+' || ref.charAt(i) == '*')) {
            i++;
        }
        if (i >= len) {
            return null;
        }
        ref = ref.substring(i);

        int colon = ref.indexOf(':');
        int slash = ref.indexOf('/');
        if (slash <= 0) {
            return null;
        }

        String type;
        if (colon != -1 && colon < slash) {
            type = ref.substring(colon + 1, slash);
        } else {
            type = ref.substring(0, slash);
        }
        return type.isEmpty() ? null : type;
    }
}