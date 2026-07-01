package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import java.util.Collection;
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
        return java.util.Collections.singletonList("type");
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        // No-op
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!"type".equals(attribute.getName())) {
            return;
        }
        org.w3c.dom.Element element = attribute.getOwnerElement();
        if (element == null || !"item".equals(element.getTagName())) {
            return;
        }
        String type = attribute.getValue();
        String value = element.getTextContent().trim();
        if (value.startsWith("@") && !value.startsWith("@null") && !value.startsWith("@android:null")) {
            int slash = value.indexOf('/');
            if (slash != -1) {
                String refType = value.substring(1, slash);
                if (refType.startsWith("+")) {
                    refType = refType.substring(1);
                }
                int colon = refType.indexOf(':');
                if (colon != -1) {
                    refType = refType.substring(colon + 1);
                }

                if (!refType.equals(type)) {
                    if ("drawable".equals(type) && "color".equals(refType)) {
                        return;
                    }
                    if ("color".equals(type) && "drawable".equals(refType)) {
                        return;
                    }

                    String name = element.getAttribute("name");
                    String message = String.format(
                            "Resource alias name \"%1$s\" of type \"%2$s\" expects %2$s value, but points to \"%3$s\"",
                            name, type, value);

                    context.report(
                            ISSUE,
                            attribute,
                            context.getLocation(attribute),
                            message
                    );
                }
            }
        }
    }
}