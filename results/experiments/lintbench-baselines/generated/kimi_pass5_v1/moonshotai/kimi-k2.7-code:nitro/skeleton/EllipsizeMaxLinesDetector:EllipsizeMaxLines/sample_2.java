package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class EllipsizeMaxLinesDetector extends LayoutDetector {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_ELLIPSIZE = "ellipsize";
    private static final String ATTR_MAX_LINES = "maxLines";

    private static final String MESSAGE =
            "Combining ellipsize with maxLines=1 can lead to crashes on some devices; "
                    + "use singleLine=\"true\" instead";

    private static final Implementation IMPLEMENTATION =
            new Implementation(EllipsizeMaxLinesDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "EllipsizeMaxLines",
                    "Combining Ellipsize and Maxlines",
                    "Setting `android:ellipsize` together with `android:maxLines=\"1\"` "
                            + "can cause crashes on some devices. If you need `ellipsize`, "
                            + "use `android:singleLine=\"true\"` instead of `maxLines=\"1\"`.",
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
        return Collections.singletonList(ATTR_ELLIPSIZE);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        Element element = attribute.getOwnerElement();
        Attr maxLines = element.getAttributeNodeNS(ANDROID_URI, ATTR_MAX_LINES);
        if (maxLines != null && "1".equals(maxLines.getValue())) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getValueLocation(attribute),
                    MESSAGE);
        }
    }
}