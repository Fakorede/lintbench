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

import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;

public class EllipsizeMaxLinesDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "EllipsizeMaxLines",
            "Combining Ellipsize and MaxLines",
            "Combining `ellipsize` and `maxLines=1` can lead to crashes on some devices. " +
            "Earlier versions of lint recommended replacing `singleLine=true` with " +
            "`maxLines=1` but that should not be done when using `ellipsize`.",
            Category.CORRECTNESS,
            7,
            Severity.WARNING,
            new Implementation(
                    EllipsizeMaxLinesDetector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    private static final String ELLIPSIZE = "ellipsize";
    private static final String MAX_LINES = "maxLines";
    private static final String SINGLE_LINE = "singleLine";

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("TextView");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (element.hasAttributeNS(ANDROID_URI, ELLIPSIZE)
                && element.hasAttributeNS(ANDROID_URI, MAX_LINES)) {
            String maxLines = element.getAttributeNS(ANDROID_URI, MAX_LINES);
            if ("1".equals(maxLines)) {
                context.report(ISSUE, element, context.getLocation(element),
                        "Combining ellipsize with maxLines=1 can cause crashes on some devices; "
                                + "use singleLine=true instead");
            }
        }
    }
}