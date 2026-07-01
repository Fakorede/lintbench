package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.ResourceFolderType;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class EllipsizeMaxLinesDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "EllipsizeMaxLines",
                    "Combining Ellipsize and Maxlines",
                    "Combining `ellipsize` and `maxLines=1` can lead to crashes on some devices. "
                            + "Earlier versions of lint recommended replacing `singleLine=true` with "
                            + "`maxLines=1`, but that should not be done when using `ellipsize`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            EllipsizeMaxLinesDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList("ellipsize", "maxLines");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!"ellipsize".equals(getLocalName(attribute))) {
            return;
        }

        String ellipsizeValue = attribute.getValue();
        if (ellipsizeValue == null || "none".equals(ellipsizeValue)) {
            return;
        }

        Element element = attribute.getOwnerElement();
        String namespace = attribute.getNamespaceURI();
        Attr maxLines =
                namespace != null
                        ? element.getAttributeNodeNS(namespace, "maxLines")
                        : element.getAttributeNode("maxLines");

        if (maxLines != null && "1".equals(maxLines.getValue())) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "Combining `ellipsize` and `maxLines=1` can lead to crashes on some devices. "
                            + "Earlier versions of lint recommended replacing `singleLine=true` with "
                            + "`maxLines=1`, but that should not be done when using `ellipsize`.");
        }
    }

    private static String getLocalName(Attr attr) {
        String local = attr.getLocalName();
        if (local != null) {
            return local;
        }
        String name = attr.getName();
        int colon = name.indexOf(':');
        return colon != -1 ? name.substring(colon + 1) : name;
    }
}