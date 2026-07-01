package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ELLIPSIZE;
import static com.android.SdkConstants.ATTR_MAX_LINES;

public class EllipsizeMaxLinesDetector extends Detector implements XmlScanner {

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

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("TextView");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Attr maxLinesAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_MAX_LINES);
        if (maxLinesAttr != null && "1".equals(maxLinesAttr.getValue())) {
            String ellipsize = element.getAttributeNS(ANDROID_URI, ATTR_ELLIPSIZE);
            if (ellipsize != null && !ellipsize.isEmpty() && !"none".equals(ellipsize)) {
                context.report(ISSUE, context.getLocation(maxLinesAttr),
                    "Combining `ellipsize` and `maxLines=\"1\"` can lead to crashes on some devices. " +
                    "Use `singleLine=\"true\"` instead.");
            }
        }
    }
}