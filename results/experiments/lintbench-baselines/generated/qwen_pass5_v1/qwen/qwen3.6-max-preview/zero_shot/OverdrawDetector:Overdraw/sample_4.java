package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.*;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;

public class OverdrawDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
        "Overdraw",
        "Painting regions more than once",
        "If you set a background drawable on a root view, then you should use a " +
        "custom theme where the theme background is null. Otherwise, the theme background " +
        "will be painted first, only to have your custom background completely cover it; " +
        "this is called \"overdraw\".\n\n" +
        "NOTE: This detector relies on figuring out which layouts are associated with " +
        "which activities based on scanning the Java code, and it's currently doing that " +
        "using an inexact pattern matching algorithm. Therefore, it can incorrectly " +
        "conclude which activity the layout is associated with and then wrongly complain " +
        "that a background-theme is hidden.\n\n" +
        "If you want your custom background on multiple pages, then you should consider " +
        "making a custom theme with your custom background and just using that theme " +
        "instead of a root element background.\n\n" +
        "Of course it's possible that your custom drawable is translucent and you want " +
        "it to be mixed with the background. However, you will get better performance " +
        "if you pre-mix the background with your drawable and use that resulting image or " +
        "color as a custom theme background instead.",
        Category.PERFORMANCE,
        3,
        Severity.WARNING,
        new Implementation(OverdrawDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("*");
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        if (!(element.getParentNode() instanceof Document)) {
            return;
        }

        String tag = element.getTagName();
        if (SdkConstants.TAG_MERGE.equals(tag) || SdkConstants.TAG_INCLUDE.equals(tag)) {
            return;
        }

        Attr background = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_BACKGROUND);
        if (background != null) {
            String value = background.getValue();
            if (value != null && !value.equals("@null") && !value.equals("@android:color/transparent")) {
                context.report(ISSUE, context.getLocation(background),
                    "Possible overdraw: Root element paints background `" + value + "`");
            }
        }
    }
}