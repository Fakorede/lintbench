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
                            + "of the same type as the alias.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    new Implementation(
                            DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private ResourceFolderType mFolderType;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList("type", "name");
    }

    @Override
    public void beforeCheckFile(@NonNull com.android.tools.lint.detector.api.Context context) {
        if (context instanceof XmlContext) {
            mFolderType = ((XmlContext) context).getResourceFolderType();
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (mFolderType != ResourceFolderType.VALUES) {
            return;
        }

        Element element = attribute.getOwnerElement();
        String tagName = element.getTagName();

        // We are interested in <item> elements that define resource aliases
        if (!"item".equals(tagName)) {
            return;
        }

        // Only process the "name" attribute to avoid double-reporting
        if (!"name".equals(attribute.getLocalName())) {
            return;
        }

        // Get the declared type of this item element
        String declaredType = element.getAttribute("type");
        if (declaredType == null || declaredType.isEmpty()) {
            return;
        }

        // Get the value of the element (the reference it points to)
        String value = element.getTextContent();
        if (value == null) {
            return;
        }
        value = value.trim();

        // Check if this is a reference (starts with @)
        if (!value.startsWith("@")) {
            return;
        }

        // Parse the reference type from @type/name or @+type/name
        String referenceBody = value.substring(1);
        if (referenceBody.startsWith("+")) {
            referenceBody = referenceBody.substring(1);
        }

        // Handle package-qualified references like @android:color/white
        int slashIndex = referenceBody.indexOf('/');
        if (slashIndex < 0) {
            return;
        }

        String referenceTypePart = referenceBody.substring(0, slashIndex);

        // Strip package prefix if present (e.g., "android:color" -> "color")
        int colonIndex = referenceTypePart.indexOf(':');
        String referenceType;
        if (colonIndex >= 0) {
            referenceType = referenceTypePart.substring(colonIndex + 1);
        } else {
            referenceType = referenceTypePart;
        }

        // Compare the declared type with the reference type
        if (!declaredType.equals(referenceType)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    String.format(
                            "Unexpected resource reference type; expected `%1$s`, got `%2$s`",
                            declaredType, referenceType));
        }
    }
}