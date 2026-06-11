package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

public class NegativeMarginDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "NegativeMargins",
            "Margin values should be positive.",
            "Margin values should be positive. Negative values are generally a sign that you are making assumptions about views surrounding the current one, or may be tempted to turn off child clipping to allow a view to escape its parent. Turning off child clipping to do this not only leads to poor graphical performance, it also results in wrong touch event handling since touch events are based strictly on a chain of parent-rect hit tests. Finally, making assumptions about the size of strings can lead to localization problems.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    NegativeMarginDetector.class,
                    Scope.ALL.getResourceFolders())
    );

    @Override
    public Issue getIssue() {
        return ISSUE;
    }

    @Override
    public List<String> getApplicableAttributes() {
        return Collections.singletonList("android:layout_margin");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value != null && value.startsWith("-")) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    "Negative margin values are discouraged.");
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        // No-op
    }
}