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
        return Collections.singletonList("type");
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!"type".equals(attribute.getLocalName()) && !"type".equals(attribute.getName())) {
            return;
        }
        org.w3c.dom.Element element = attribute.getOwnerElement();
        if (!"item".equals(element.getTagName())) {
            return;
        }

        String ref = element.getTextContent().trim();
        if (ref.startsWith("@") && !ref.startsWith("@+")) {
            int slash = ref.indexOf('/');
            if (slash != -1) {
                String typePart = ref.substring(1, slash);
                int colon = typePart.indexOf(':');
                String refType = colon != -1 ? typePart.substring(colon + 1) : typePart;

                String aliasType = attribute.getValue();
                if (!aliasType.equals(refType)) {
                    context.report(
                            ISSUE,
                            attribute,
                            context.getLocation(attribute),
                            "The resource alias type '" + aliasType + "' does not match the reference type '" + refType + "'");
                }
            }
        }
    }
}