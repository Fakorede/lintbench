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

    public static final Issue ISSUE =
            Issue.create(
                    "DensityIndependentDrawable",
                    "Image defined in density-independent drawable folder",
                    "The res/drawable folder is intended for density-independent graphics such as shapes defined in XML. For bitmaps, move it to `drawable-mdpi` and consider providing higher and lower resolution versions in `drawable-ldpi`, `drawable-hdpi` and `drawable-xhdpi`. If the icon **really** is density independent (for example a solid color) you can place it in `drawable-nodpi`.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            IconDetector.class,
                            Collections.emptyList()));

    @Override
    public List<Issue> getApplicableIssues() {
        return Collections.singletonList(ISSUE);
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String resourceName = context.getDriver().getResourceName(context.getFile());
        if (resourceName != null && !resourceName.isEmpty()) {
            checkDrawable(context, element);
        }
    }

    private void checkDrawable(XmlContext context, Element element) {
        String nodeName = element.getNodeName();
        if ("bitmap".equals(nodeName)) {
            reportIssue(context, element);
        } else if ("item".equals(nodeName)) {
            String formatAttrValue = element.getAttribute("format");
            if (formatAttrValue != null && "png".equalsIgnoreCase(formatAttrValue)) {
                reportIssue(context, element);
            }
        }
    }

    private void reportIssue(XmlContext context, Element element) {
        context.report(
                ISSUE,
                context.getLocation(element),
                "Bitmaps should not be placed in the density-independent drawable folder. Move it to `drawable-mdpi` and consider providing higher and lower resolution versions.");
    }
}