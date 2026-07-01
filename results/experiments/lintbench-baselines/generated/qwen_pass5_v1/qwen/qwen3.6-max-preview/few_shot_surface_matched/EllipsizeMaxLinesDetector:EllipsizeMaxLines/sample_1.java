package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ELLIPSIZE;
import static com.android.SdkConstants.ATTR_MAX_LINES;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import java.util.Arrays;
import java.util.Collection;

public class EllipsizeMaxLinesDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "EllipsizeMaxLines",
                    "Combining ellipsize and maxLines=1",
                    "Combining `ellipsize` and `maxLines=1` can lead to crashes on some devices. "
                            + "Earlier versions of lint recommended replacing `singleLine=true` with "
                            + "`maxLines=1` but that should not be done when using `ellipsize`.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(
                            EllipsizeMaxLinesDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTR_MAX_LINES, ATTR_ELLIPSIZE);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ATTR_MAX_LINES.equals(attribute.getLocalName())) {
            return;
        }

        if (!"1".equals(attribute.getValue())) {
            return;
        }

        Element element = attribute.getOwnerElement();
        String ellipsize = element.getAttributeNS(ANDROID_URI, ATTR_ELLIPSIZE);

        if (ellipsize != null && !ellipsize.isEmpty() && !"none".equals(ellipsize)) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "Combining `ellipsize` and `maxLines=1` can lead to crashes on some devices");
        }
    }
}