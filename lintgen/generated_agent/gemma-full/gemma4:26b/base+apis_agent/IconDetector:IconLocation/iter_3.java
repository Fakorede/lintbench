package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.*;
import org.w3c.dom.Element;

import java.util.Collections;

public class IconDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ImageInDensityIndependentDrawableFolder",
            "Bitmap in density-independent folder",
            "Bitmaps should be placed in density-specific drawable folders (e.g., drawable-hdpi) or drawable-nodpi, rather than the generic res/drawable folder.",
            BugCategory.CORRECTNESS,
            10,
            Severity.WARNING,
            new Implementation(new IconDetector(), null)
    );

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return Collections.singletonList("bitmap");
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        // We only care about the generic 'res/drawable' folder.
        // If a <bitmap> tag is found here, it means an image is being defined 
        // in a density-independent folder.
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Found a <bitmap> tag in the generic res/drawable folder. " +
                        "Bitmaps should be placed in density-specific drawable folders (e.g., drawable-hdpi) or drawable-nodpi."
        );
    }
}