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
import java.util.EnumSet;

public class DuplicateResourceDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ReferenceType",
                    "Incorrect reference types",
                    "When you generate a resource alias, the resource you are pointing to must be "
                            + "of the same type as the alias. For example, a `@string` alias must "
                            + "point to a `@string` resource, not a `@color` or other type.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    new Implementation(
                            DuplicateResourceDetector.class,
                            Scope.RESOURCE_FILE_SCOPE));

    /** The current file's folder type */
    private ResourceFolderType mFolderType;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList("name", "type");
    }

    @Override
    public void beforeCheckFile(@NonNull com.android.tools.lint.detector.api.Context context) {
        if (context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            mFolderType = xmlContext.getResourceFolderType();
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (mFolderType != ResourceFolderType.VALUES) {
            return;
        }

        Element element = attribute.getOwnerElement();
        String tagName = element.getTagName();

        // We only care about <item> elements that define resource aliases
        if (!"item".equals(tagName)) {
            return;
        }

        // Get the type attribute of the <item> element
        String itemType = element.getAttribute("type");
        if (itemType == null || itemType.isEmpty()) {
            return;
        }

        // Get the text content of the element, which should be a reference
        String textContent = element.getTextContent();
        if (textContent == null) {
            return;
        }
        textContent = textContent.trim();

        // Check if the text content is a resource reference
        if (!textContent.startsWith("@")) {
            return;
        }

        // Strip leading '@' and optional '+' (for new IDs)
        String reference = textContent.substring(1);
        if (reference.startsWith("+")) {
            reference = reference.substring(1);
        }

        // Handle package-qualified references like @android:string/foo
        // Strip the package prefix if present
        int colonIndex = reference.indexOf(':');
        if (colonIndex != -1) {
            reference = reference.substring(colonIndex + 1);
        }

        // Now reference should be like "type/name"
        int slashIndex = reference.indexOf('/');
        if (slashIndex == -1) {
            return;
        }

        String referencedType = reference.substring(0, slashIndex);

        // Compare the item type with the referenced resource type
        if (!itemType.equals(referencedType)) {
            String message = String.format(
                    "Unexpected resource reference type; expected value of type `@%1$s/`",
                    itemType);
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    message);
        }
    }
}