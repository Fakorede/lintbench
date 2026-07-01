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

public class EllipsizeMaxLinesDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(EllipsizeMaxLinesDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "EllipsizeMaxLines",
                    "Combining Ellipsize and Maxlines",
                    "Combining `ellipsize` and `maxLines=1` can lead to crashes on some devices. " +
                    "Earlier versions of lint recommended replacing `singleLine=true` with `maxLines=1` " +
                    "but that should not be done when using `ellipsize`.",
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
        return Arrays.asList("maxLines", "ellipsize");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // Only trigger the check when visiting maxLines to avoid duplicate reports per element
        if (!"maxLines".equals(attribute.getLocalName())) {
            return;
        }

        Element element = attribute.getOwnerElement();
        String maxLinesValue = attribute.getValue();
        String ellipsizeValue = element.getAttributeNS(
                "http://schemas.android.com/apk/res/android", "ellipsize");

        if ("1".equals(maxLinesValue)
                && ellipsizeValue != null
                && !ellipsizeValue.isEmpty()
                && !"none".equals(ellipsizeValue)) {
            context.report(ISSUE, context.getLocation(attribute),
                    "Combining `ellipsize` and `maxLines=\"1\"` can lead to crashes on some devices. " +
                    "Use `android:singleLine=\"true\"` instead.");
        }
    }
}