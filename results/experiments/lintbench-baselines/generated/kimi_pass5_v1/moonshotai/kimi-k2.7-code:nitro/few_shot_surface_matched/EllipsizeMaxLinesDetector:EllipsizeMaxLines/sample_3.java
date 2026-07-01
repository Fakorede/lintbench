package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.ResourceFolderType;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class EllipsizeMaxLinesDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "EllipsizeMaxLines",
                    "Combining Ellipsize and MaxLines",
                    "Combining `ellipsize` and `maxLines=1` can lead to crashes on some devices."
                            + " Earlier versions of lint recommended replacing `singleLine=true`"
                            + " with `maxLines=1`, but that should not be done when using"
                            + " `ellipsize`.",
                    Category.CORRECTNESS,
                    7,
                    Severity.ERROR,
                    new Implementation(
                            EllipsizeMaxLinesDetector.class,
                            Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("ellipsize");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String name = attribute.getLocalName();
        if (name == null) {
            name = attribute.getName();
        }
        if (!"ellipsize".equals(name)) {
            return;
        }

        Element owner = attribute.getOwnerElement();
        if (owner == null) {
            return;
        }

        String namespace = attribute.getNamespaceURI();
        String maxLines;
        if (namespace != null) {
            maxLines = owner.getAttributeNS(namespace, "maxLines");
        } else {
            maxLines = owner.getAttribute("maxLines");
        }

        if ("1".equals(maxLines.trim())) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "Combining `ellipsize` and `maxLines=1` can lead to crashes on some devices; "
                            + "do not use `maxLines=1` with `ellipsize`");
        }
    }
}