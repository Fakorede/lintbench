package com.android.tools.lint.checks;

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

public class EllipsizeMaxLinesDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(EllipsizeMaxLinesDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "EllipsizeMaxLines",
                    "Combining Ellipsize and Maxlines",
                    "Combining `ellipsize` and `maxLines=1` can lead to crashes on some devices. "
                            + "Earlier versions of lint recommended replacing `singleLine=true` "
                            + "with `maxLines=1`, but that should not be done when using `ellipsize`.",
                    Category.CORRECTNESS,
                    4,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_ELLIPSIZE = "ellipsize";
    private static final String ATTR_MAX_LINES = "maxLines";

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ELLIPSIZE);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!ATTR_ELLIPSIZE.equals(attribute.getLocalName())) {
            return;
        }

        Attr maxLinesAttr =
                attribute.getOwnerElement().getAttributeNodeNS(ANDROID_URI, ATTR_MAX_LINES);
        if (maxLinesAttr == null) {
            return;
        }

        String maxLinesValue = maxLinesAttr.getValue();
        if (!maxLinesValue.isEmpty()) {
            try {
                int maxLines = Integer.parseInt(maxLinesValue);
                if (maxLines == 1) {
                    String message =
                            "Combining `ellipsize` and `maxLines=1` can cause crashes on some "
                                    + "devices; consider removing `ellipsize` or increasing "
                                    + "`maxLines`";
                    context.report(ISSUE, context.getLocation(attribute), message);
                }
            } catch (NumberFormatException ignored) {
            }
        }
    }
}