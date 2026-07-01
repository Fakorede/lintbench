package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;

public class DuplicateResourceDetector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ReferenceType",
                    "Incorrect reference types",
                    "When you generate a resource alias, the resource you are pointing to must "
                            + "be of the same type as the alias",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    new Implementation(
                            DuplicateResourceDetector.class,
                            Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("type");
    }

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.VALUES;
    }

    @Override
    public void beforeCheckFile(XmlContext context) {
        super.beforeCheckFile(context);
    }

    @Override
    public void visitAttribute(XmlContext context, org.w3c.dom.Attr attribute) {
        org.w3c.dom.Element element = attribute.getOwnerElement();
        if (!"item".equals(element.getTagName())) {
            return;
        }
        String aliasType = attribute.getValue();
        String value = element.getTextContent().trim();
        if (value.startsWith("@") && !value.startsWith("@+")) {
            int slash = value.indexOf('/');
            if (slash != -1) {
                int start = value.indexOf(':');
                if (start == -1 || start > slash) {
                    start = value.indexOf('@');
                }
                if (start != -1 && start < slash) {
                    String targetType = value.substring(start + 1, slash);
                    if (!aliasType.equals(targetType)) {
                        if ("drawable".equals(aliasType) && "color".equals(targetType)) {
                            return;
                        }
                        context.report(
                                ISSUE,
                                attribute,
                                context.getLocation(attribute),
                                String.format(
                                        "Difference in type value (%1$s) and target reference type (%2$s)",
                                        aliasType, targetType));
                    }
                }
            }
        }
    }
}