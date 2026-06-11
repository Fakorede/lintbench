package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

public class PxUsageDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "PxUsage",
            "Using `dp` instead of `sp` for text sizes",
            "When setting text sizes, you should normally use `sp`, or \"scale-independent pixels\". This is like the `dp` unit, but it is also scaled by the user's font size preference. It is recommended that you use this unit when specifying font sizes, so they will be adjusted for both the screen density and the user's preference.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(PxUsageDetector.class, true)
    );

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("android:textSize");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value != null && value.endsWith("dp")) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    "Use `sp` instead of `dp` for text sizes");
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }
}