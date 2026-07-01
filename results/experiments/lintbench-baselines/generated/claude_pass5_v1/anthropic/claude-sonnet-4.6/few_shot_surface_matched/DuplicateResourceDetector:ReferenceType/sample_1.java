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

    private String mCurrentFolderType;

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
        mCurrentFolderType = null;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Element element = attribute.getOwnerElement();
        String tagName = element.getTagName();

        // We only care about <item> elements which define resource aliases
        if (!"item".equals(tagName)) {
            return;
        }

        String typeAttr = element.getAttribute("type");
        if (typeAttr == null || typeAttr.isEmpty()) {
            return;
        }

        // Only check the "name" attribute to avoid double-reporting
        if (!"name".equals(attribute.getLocalName())) {
            return;
        }

        String value = element.getTextContent();
        if (value == null) {
            return;
        }
        value = value.trim();

        // Check if the value is a resource reference
        if (!value.startsWith("@")) {
            return;
        }

        // Parse the reference type: @type/name or @+type/name
        String reference = value.substring(1);
        if (reference.startsWith("+")) {
            reference = reference.substring(1);
        }

        int slashIndex = reference.indexOf('/');
        if (slashIndex < 0) {
            return;
        }

        String referenceType = reference.substring(0, slashIndex).trim();

        // Normalize android namespace prefix if present (e.g. "android:string" -> "string")
        if (referenceType.contains(":")) {
            referenceType = referenceType.substring(referenceType.indexOf(':') + 1);
        }

        // Compare the alias type with the referenced resource type
        if (!typeAttr.equals(referenceType)) {
            String message =
                    String.format(
                            "Unexpected resource reference type; expected value of type `@%1$s/`"
                                    + " but was `@%2$s/`",
                            typeAttr, referenceType);
            context.report(
                    ISSUE,
                    element,
                    context.getValueLocation(attribute),
                    message);
        }
    }
}