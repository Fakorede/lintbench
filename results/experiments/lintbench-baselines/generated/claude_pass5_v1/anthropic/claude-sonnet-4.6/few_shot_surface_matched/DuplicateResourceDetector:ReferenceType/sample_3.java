package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;

public class DuplicateResourceDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ReferenceType",
                    "Incorrect reference types",
                    "When you generate a resource alias, the resource you are pointing to must be"
                            + " of the same type as the alias. For example, a `@string` alias must"
                            + " point to a `@string` resource, not a `@drawable` or other type.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final String ATTR_TYPE = "type";
    private static final String ATTR_NAME = "name";

    /** The current file's resource folder type */
    private ResourceFolderType mFolderType;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public void beforeCheckFile(@NonNull com.android.tools.lint.detector.api.Context context) {
        if (context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            mFolderType = xmlContext.getResourceFolderType();
        }
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_TYPE);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Element element = attribute.getOwnerElement();
        String tagName = element.getTagName();

        // We're looking for <item type="..." ...>@type/name</item> patterns
        // where the reference type doesn't match the declared type
        if (!"item".equals(tagName)) {
            return;
        }

        String declaredType = attribute.getValue();
        if (declaredType == null || declaredType.isEmpty()) {
            return;
        }

        // Get the text content of the element (the reference value)
        String textContent = element.getTextContent();
        if (textContent == null) {
            return;
        }
        textContent = textContent.trim();

        // Check if the value is a resource reference (starts with @)
        if (!textContent.startsWith("@")) {
            return;
        }

        // Strip the leading '@' and optional '+'
        String reference = textContent.substring(1);
        if (reference.startsWith("+")) {
            reference = reference.substring(1);
        }

        // Check for package-qualified references (e.g., @android:string/foo)
        int slashIndex = reference.indexOf('/');
        if (slashIndex == -1) {
            // Not a valid reference format
            return;
        }

        String referencedType = reference.substring(0, slashIndex);

        // Handle package-qualified type (e.g., "android:string" -> "string")
        int colonIndex = referencedType.indexOf(':');
        if (colonIndex != -1) {
            referencedType = referencedType.substring(colonIndex + 1);
        }

        // Compare the declared type with the referenced type
        if (!declaredType.equals(referencedType)) {
            String nameAttr = element.getAttribute(ATTR_NAME);
            String message =
                    String.format(
                            "Wrong resource type: `%1$s` has type `%2$s` but references a"
                                    + " `%3$s` resource",
                            nameAttr != null && !nameAttr.isEmpty() ? nameAttr : "resource",
                            declaredType,
                            referencedType);
            context.report(ISSUE, attribute, context.getLocation(attribute), message);
        }
    }
}