package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.BugCategory;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.Severity;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;

public class IconDetector extends Detector implements XmlScanner {

    private static final String ID = "ImageInDensityIndependentDrawableFolder";
    private static final String SHORT_DESC = "Bitmap in density-independent folder";
    private static final String LONG_DESC = "Bitmaps should be placed in density-specific drawable folders (e.g., drawable-hdpi) or drawable-nodpi, rather than the generic res/drawable folder.";

    public static final Issue ISSUE = Issue.create(
            ID,
            SHORT_DESC,
            LONG_DESC,
            BugCategory.CORRECTNESS,
            10,
            Severity.WARNING,
            new Implementation(new IconDetector(), null)
    );

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(
                "android:src",
                "android:background",
                "app:srcCompat",
                "android:drawableTop",
                "android:drawableLeft",
                "android:drawableRight",
                "android:drawableBottom",
                "android:drawableStart",
                "android:drawableEnd",
                "android:tint"
        );
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        // We only care about the generic 'res/drawable' folder.
        return folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public void visitAttribute(XmlContext context, Element element, Attr attribute) {
        String value = attribute.getValue();
        if (value == null || !value.startsWith("@drawable/")) {
            return;
        }

        String resourceName = value.substring(9);

        // If we are in the generic 'res/drawable' folder, any drawable reference 
        // that points to a resource found here is potentially a bitmap that should be moved.
        // We check if the resource exists in this specific folder context.
        if (context.findResource(resourceName, ResourceType.DRAWABLE) != null) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "Found drawable '" + resourceName + "' in the generic res/drawable folder. " +
                            "If this is a bitmap, move it to a density-specific folder (e.g., drawable-hdpi) or drawable-nodpi."
            );
        }
    }
}