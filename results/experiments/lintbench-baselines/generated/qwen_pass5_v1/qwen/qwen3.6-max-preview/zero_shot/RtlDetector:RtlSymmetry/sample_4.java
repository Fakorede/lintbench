package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.jetbrains.annotations.NonNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collection;

public class RtlDetector extends ResourceXmlDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    public static final Issue ISSUE = Issue.create(
            "RtlSymmetry",
            "Padding and margin symmetry",
            "If you specify padding or margin on the left side of a layout, you should " +
            "probably also specify padding on the right side (and vice versa) for " +
            "right-to-left layout symmetry.",
            Category.RTL,
            Severity.WARNING,
            new Implementation(RtlDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        checkSymmetry(context, element, "paddingLeft", "paddingRight");
        checkSymmetry(context, element, "layout_marginLeft", "layout_marginRight");
    }

    private void checkSymmetry(@NonNull XmlContext context, @NonNull Element element,
                               @NonNull String leftAttr, @NonNull String rightAttr) {
        Attr left = element.getAttributeNodeNS(ANDROID_URI, leftAttr);
        Attr right = element.getAttributeNodeNS(ANDROID_URI, rightAttr);

        if (left != null && right == null) {
            String message = String.format(
                    "When specifying `android:%s` you should also specify `android:%s` for RTL symmetry",
                    leftAttr, rightAttr);
            context.report(ISSUE, left, context.getLocation(left), message);
        } else if (left == null && right != null) {
            String message = String.format(
                    "When specifying `android:%s` you should also specify `android:%s` for RTL symmetry",
                    rightAttr, leftAttr);
            context.report(ISSUE, right, context.getLocation(right), message);
        }
    }
}