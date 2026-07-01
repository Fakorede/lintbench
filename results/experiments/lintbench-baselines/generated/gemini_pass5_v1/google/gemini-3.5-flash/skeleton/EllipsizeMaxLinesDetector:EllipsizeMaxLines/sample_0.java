package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.*;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class EllipsizeMaxLinesDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(EllipsizeMaxLinesDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "EllipsizeMaxLines",
                    "Combining Ellipsize and Maxlines",
                    "Combining `ellipsize` and `maxLines=1` can lead to crashes on some devices. "
                            + "Earlier versions of lint recommended replacing `singleLine=true` with "
                            + "`maxLines=1` but that should not be done when using `ellipsize`.",
                    Category.CORRECTNESS,
                    4,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("ellipsize");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!"ellipsize".equals(attribute.getLocalName())) {
            return;
        }
        Element element = attribute.getOwnerElement();
        if (element == null) {
            return;
        }
        Attr maxLinesAttr = element.getAttributeNodeNS("http://schemas.android.com/apk/res/android", "maxLines");
        if (maxLinesAttr != null && "1".equals(maxLinesAttr.getValue())) {
            context.report(
                    ISSUE,
                    maxLinesAttr,
                    context.getLocation(maxLinesAttr),
                    "Combining `ellipsize` and `maxLines=\"1\"` can lead to crashes on some devices. Use `singleLine=\"true\"` instead.");
        }
    }
}