package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
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
                    "but that should not be done when using `ellipsize`. Use `singleLine=true` instead.",
                    Category.CORRECTNESS,
                    4,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

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
        // Only process maxLines to avoid duplicate reports when both attributes are present
        if (!"maxLines".equals(attribute.getLocalName())) {
            return;
        }

        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        if ("1".equals(attribute.getValue())) {
            Element element = attribute.getOwnerElement();
            String ellipsize = element.getAttributeNS(ANDROID_URI, "ellipsize");
            if (ellipsize != null && !ellipsize.isEmpty()) {
                context.report(
                        ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        "Combining `ellipsize` and `maxLines=\"1\"` can lead to crashes on some devices. " +
                        "Use `singleLine=\"true\"` instead.");
            }
        }
    }
}