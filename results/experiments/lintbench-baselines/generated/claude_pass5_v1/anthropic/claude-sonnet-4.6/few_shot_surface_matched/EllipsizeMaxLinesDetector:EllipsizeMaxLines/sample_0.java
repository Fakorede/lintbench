package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ELLIPSIZE;
import static com.android.SdkConstants.ATTR_MAX_LINES;
import static com.android.SdkConstants.ATTR_SINGLE_LINE;
import static com.android.SdkConstants.FD_RES_LAYOUT;

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
import java.util.EnumSet;

public class EllipsizeMaxLinesDetector extends LayoutDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "EllipsizeMaxLines",
                    "Combining Ellipsize and Maxlines",
                    "Combining `ellipsize` and `maxLines=1` can lead to crashes on some devices. "
                            + "Earlier versions of lint recommended replacing `singleLine=true` "
                            + "with `maxLines=1` but that should not be done when using "
                            + "`ellipsize`.",
                    Category.CORRECTNESS,
                    8,
                    Severity.ERROR,
                    new Implementation(
                            EllipsizeMaxLinesDetector.class,
                            Scope.RESOURCE_FILE_SCOPE));

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

        String ellipsizeValue = element.getAttributeNS(ANDROID_URI, ATTR_ELLIPSIZE);
        String maxLinesValue = element.getAttributeNS(ANDROID_URI, ATTR_MAX_LINES);

        if (ellipsizeValue != null && !ellipsizeValue.isEmpty()
                && maxLinesValue != null && maxLinesValue.equals("1")) {
            String attributeName = attribute.getLocalName();
            Attr reportAttr;
            if (ATTR_MAX_LINES.equals(attributeName)) {
                reportAttr = attribute;
            } else {
                // Report on the maxLines attribute if we're visiting ellipsize,
                // but if maxLines isn't set yet just report on current attribute
                Attr maxLinesAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_MAX_LINES);
                reportAttr = maxLinesAttr != null ? maxLinesAttr : attribute;
            }

            context.report(
                    ISSUE,
                    reportAttr,
                    context.getLocation(reportAttr),
                    "Combining `ellipsize` and `maxLines=1` can lead to crashes on some "
                            + "devices. Consider using `singleLine=true` instead.");
        }
    }
}