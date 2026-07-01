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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class DuplicateResourceDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ReferenceType",
                    "Incorrect reference types",
                    "When you create a resource alias in a values file, the value must be a "
                            + "reference to a resource of the same type as the alias. For "
                            + "example, a `<drawable>` alias must reference `@drawable/...`, "
                            + "and an `<item type=\"drawable\">` alias must also reference a "
                            + "drawable. Aliases that reference a different resource type "
                            + "will fail at runtime.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("name");
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        // No per-file state is required for the reference type check.
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Element element = attribute.getOwnerElement();
        if (!isTopLevelResource(element)) {
            return;
        }

        String declaredType = getDeclaredType(element);
        if (declaredType == null || declaredType.isEmpty()) {
            return;
        }

        if (!hasOnlyTextContent(element)) {
            return;
        }

        String value = element.getTextContent().trim();
        String referencedType = getReferencedType(value);
        if (referencedType == null) {
            return;
        }

        if (!declaredType.equals(referencedType)) {
            String name = attribute.getValue();
            String message =
                    String.format(
                            "Resource alias '%1$s' is declared as type '%2$s' but references a "
                                    + "'%3$s' resource. Resource aliases must reference the same "
                                    + "type.",
                            name, declaredType, referencedType);
            context.report(ISSUE, element, context.getLocation(element), message);
        }
    }

    private static boolean isTopLevelResource(@NonNull Element element) {
        Node parent = element.getParentNode();
        return parent != null && "resources".equals(parent.getNodeName());
    }

    private static String getDeclaredType(@NonNull Element element) {
        String tag = element.getNodeName();
        if ("item".equals(tag)) {
            String type = element.getAttribute("type");
            if (type != null && !type.isEmpty()) {
                return type;
            }
            return null;
        }
        return tag;
    }

    private static boolean hasOnlyTextContent(@NonNull Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            short type = children.item(i).getNodeType();
            if (type == Node.ELEMENT_NODE || type == Node.CDATA_SECTION_NODE) {
                return false;
            }
        }
        return true;
    }

    private static String getReferencedType(@NonNull String value) {
        String s = value.trim();
        if (!s.startsWith("@")) {
            return null;
        }

        s = s.substring(1);

        if (s.startsWith("+")) {
            s = s.substring(1);
        }

        if (s.startsWith("*")) {
            s = s.substring(1);
        }

        int colon = s.indexOf(':');
        if (colon != -1) {
            s = s.substring(colon + 1);
        }

        int slash = s.indexOf('/');
        if (slash <= 0) {
            return null;
        }

        return s.substring(0, slash);
    }
}