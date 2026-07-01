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

import java.util.Arrays;
import java.util.Collection;

public class DuplicateResourceDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ReferenceType",
                    "Incorrect reference types",
                    "When you generate a resource alias, the resource you are pointing to must be "
                            + "of the same type as the alias. For example, if you create a "
                            + "`string` alias, the reference must point to a `string` resource.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    new Implementation(
                            DuplicateResourceDetector.class,
                            Scope.RESOURCE_FILE_SCOPE));

    /** The current file's resource folder type */
    private ResourceFolderType mFolderType;

    public DuplicateResourceDetector() {
    }

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
        return Arrays.asList("type");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Element element = attribute.getOwnerElement();
        String tagName = element.getTagName();

        // We only care about <item> elements that define resource aliases
        if (!"item".equals(tagName)) {
            return;
        }

        // The "type" attribute tells us what resource type this item is declared as
        String declaredType = attribute.getValue();
        if (declaredType == null || declaredType.isEmpty()) {
            return;
        }

        // Get the text content of the element — this should be a reference like @string/foo
        String textContent = element.getTextContent();
        if (textContent == null) {
            return;
        }
        textContent = textContent.trim();

        // Check if it's a reference (starts with @)
        if (!textContent.startsWith("@")) {
            return;
        }

        // Strip leading @ and optional package prefix (e.g. @android:string/foo -> string/foo)
        String reference = textContent.substring(1);

        // Remove package qualifier if present (e.g. "android:string/foo" -> "string/foo")
        int colonIndex = reference.indexOf(':');
        if (colonIndex >= 0) {
            reference = reference.substring(colonIndex + 1);
        }

        // Now reference should be of the form "type/name"
        int slashIndex = reference.indexOf('/');
        if (slashIndex < 0) {
            return;
        }

        String referencedType = reference.substring(0, slashIndex);

        // Compare declared type vs referenced type
        if (!declaredType.equals(referencedType)) {
            String message =
                    String.format(
                            "Wrong resource type: Resource alias `%1$s` has type `%2$s` but "
                                    + "references a resource of type `%3$s`",
                            element.getAttribute("name"),
                            declaredType,
                            referencedType);
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(attribute),
                    message);
        }
    }
}