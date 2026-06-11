package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

public class IconDetector extends Detector implements XmlScanner {

    private static final Issue ISSUE = Issue.create(
            "DensityIndependentDrawable",
            "Image defined in density-independent drawable folder",
            "The res/drawable folder is intended for density-independent graphics such as shapes defined in XML. For bitmaps, move it to `drawable-mdpi` and consider providing higher and lower resolution versions in `drawable-ldpi`, `drawable-hdpi` and `drawable-xhdpi`. If the icon **really** is density independent (for example a solid color) you can place it in `drawable-nodpi`.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    IconDetector.class,
                    Collections.emptySet())
    );

    @Override
    public List<Issue> getApplicableIssues() {
        return Collections.singletonList(ISSUE);
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.DRAWABLE.equals(folderType);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String nodeName = element.getNodeName();
        if ("bitmap".equals(nodeName)) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Bitmap defined in density-independent drawable folder. Move it to `drawable-mdpi` and consider providing higher and lower resolution versions.");
        }
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        // Check if the file is a bitmap file in drawable directory.
        String fileName = context.getFilePath().getName();
        if (fileName.endsWith(".png") || fileName.endsWith(".jpg")) {
            context.report(ISSUE, document, context.getLocation(document),
                    "Bitmap defined in density-independent drawable folder. Move it to `drawable-mdpi` and consider providing higher and lower resolution versions.");
        }
    }

}