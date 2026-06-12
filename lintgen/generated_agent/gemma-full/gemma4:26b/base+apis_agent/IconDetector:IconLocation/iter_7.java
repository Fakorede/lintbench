package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.BugCategory;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "ImageInDensityIndependentDrawableFolder",
            "Bitmap in density-independent folder",
            "Bitmaps should be placed in density-specific drawable folders (e.g., drawable-hdpi) or drawable-nodpi, rather than the generic res/drawable folder.",
            BugCategory.CORRECTNESS,
            10,
            Severity.WARNING,
            new Implementation(IconDetector.class, null)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("bitmap");
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        // ResourceFolderType.DRAWABLE refers to the generic res/drawable folder.
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Found a <bitmap> tag in the generic res/drawable folder. Bitmaps should be placed in density-specific drawable folders (e.g., drawable-hdpi) or drawable-nodpi."
        );
    }
}