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
                    "When you generate a resource alias, the resource you are pointing to must be of the same type as the alias",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("*");
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        // No persistent state to reset
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value.startsWith("@")) {
            String refType = getRefType(value);
            if (refType != null) {
                Element element = attribute.getOwnerElement();
                String expectedType = getExpectedType(element);
                if (expectedType != null && !expectedType.equals(refType)) {
                    context.report(ISSUE, attribute, context.getLocation(attribute),
                            "Resource reference type mismatch: expected @" + expectedType + " but found @" + refType);
                }
            }
        }
    }

    private static String getRefType(String reference) {
        String ref = reference.startsWith("@+") ? reference.substring(2) : reference.substring(1);
        int slash = ref.indexOf('/');
        if (slash != -1) {
            String typePart = ref.substring(0, slash);
            int colon = typePart.indexOf(':');
            return colon != -1 ? typePart.substring(colon + 1) : typePart;
        }
        return null;
    }

    private static String getExpectedType(Element element) {
        String tag = element.getTagName();
        if ("item".equals(tag)) {
            String type = element.getAttribute("type");
            return type.isEmpty() ? null : type;
        }
        return tag;
    }
}