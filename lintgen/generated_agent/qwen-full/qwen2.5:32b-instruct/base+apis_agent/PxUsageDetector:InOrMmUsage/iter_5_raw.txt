package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Attr;

import java.util.Collections;
import java.util.List;

public class PxUsageDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "PxUsage",
            "Avoid using `mm` (millimeters) or `in` (inches) as the unit for dimensions.",
            "While it should work in principle, unfortunately many devices do not report " +
                    "the correct true physical density, which means that the dimension calculations " +
                    "won't work correctly. You are better off using `dp` (and for font sizes, `sp`).",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    PxUsageDetector.class,
                    Collections.emptyList())
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.LAYOUT.equals(folderType)
                || ResourceFolderType.MENU.equals(folderType);
    }

    @Override
    public List<String> getApplicableAttributes() {
        return Collections.singletonList(".*");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value != null && (value.contains("mm") || value.contains("in"))) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    "Avoid using `mm` or `in`. Use `dp` instead.");
        }
    }

    @Override
    public List<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void visitElement(XmlContext context, Element element) {}

    @Override
    public void visitDocument(XmlContext context, Document document) {}
}