package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.AnnotationInfo;
import com.android.tools.lint.detector.api.AnnotationUsageInfo;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.XmlContext;

import org.jetbrains.uast.UElement;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

public class IconDetector extends ResourceXmlDetector {

    private static final Issue ISSUE =
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
    protected int getApplicableElements() {
        return XML;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String resourceName = context.getResourceName();
        if (resourceName != null && !resourceName.isEmpty()) {
            ResourceFolderType folderType = context.getFolderType();
            if (folderType == ResourceFolderType.DRAWABLE) {
                checkDrawable(context, element);
            }
        }
    }

    private void checkDrawable(XmlContext context, Element element) {
        String nodeName = element.getNodeName();
        if ("bitmap".equals(nodeName)) {
            reportIssue(context, element);
        } else if ("item".equals(nodeName)) {
            Attr formatAttr = element.getAttributeNode("format");
            if (formatAttr != null && "png".equalsIgnoreCase(formatAttr.getValue())) {
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

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE;
    }
}