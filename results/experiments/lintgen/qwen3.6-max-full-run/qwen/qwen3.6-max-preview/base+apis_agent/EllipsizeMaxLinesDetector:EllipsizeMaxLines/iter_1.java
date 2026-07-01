package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;

public class EllipsizeMaxLinesDetector extends Detector implements Detector.XmlScanner {
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_MAX_LINES = "maxLines";
    private static final String ATTR_ELLIPSIZE = "ellipsize";

    public static final Issue ISSUE = Issue.create(
        "EllipsizeMaxLines",
        "Combining ellipsize and maxLines=1 can lead to crashes",
        "Combining `ellipsize` and `maxLines=1` can lead to crashes on some devices. " +
        "Earlier versions of lint recommended replacing `singleLine=true` with `maxLines=1` " +
        "but that should not be done when using `ellipsize`.",
        Category.CORRECTNESS,
        6,
        Severity.WARNING,
        new Implementation(EllipsizeMaxLinesDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_MAX_LINES);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!"1".equals(attribute.getValue())) {
            return;
        }

        Element element = attribute.getOwnerElement();
        if (element.hasAttributeNS(ANDROID_URI, ATTR_ELLIPSIZE)) {
            context.report(ISSUE, context.getLocation(attribute),
                "Combining `ellipsize` and `maxLines=1` can lead to crashes. Use `singleLine=true` instead.");
        }
    }
}