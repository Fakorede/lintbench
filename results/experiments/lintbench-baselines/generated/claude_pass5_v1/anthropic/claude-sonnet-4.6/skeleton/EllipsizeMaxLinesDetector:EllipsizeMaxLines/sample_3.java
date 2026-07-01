package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

public class EllipsizeMaxLinesDetector extends LayoutDetector {

    private static final String ATTR_ELLIPSIZE = "ellipsize";
    private static final String ATTR_MAX_LINES = "maxLines";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

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
        return Arrays.asList(ATTR_ELLIPSIZE, ATTR_MAX_LINES);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Element element = attribute.getOwnerElement();
        NamedNodeMap attributes = element.getAttributes();

        Attr ellipsizeAttr = (Attr) attributes.getNamedItemNS(ANDROID_NS, ATTR_ELLIPSIZE);
        Attr maxLinesAttr = (Attr) attributes.getNamedItemNS(ANDROID_NS, ATTR_MAX_LINES);

        if (ellipsizeAttr == null || maxLinesAttr == null) {
            return;
        }

        String maxLinesValue = maxLinesAttr.getValue();
        if (!"1".equals(maxLinesValue)) {
            return;
        }

        String ellipsizeValue = ellipsizeAttr.getValue();
        if (ellipsizeValue == null || ellipsizeValue.isEmpty()) {
            return;
        }

        // Only report on one of the two attributes to avoid duplicate warnings.
        // Report on maxLines since that's the one that should be changed back to singleLine.
        if (attribute.getLocalName().equals(ATTR_MAX_LINES)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(attribute),
                    "Combining `ellipsize` and `maxLines=1` can lead to crashes on some "
                            + "devices. Use `singleLine=true` instead.");
        }
    }
}