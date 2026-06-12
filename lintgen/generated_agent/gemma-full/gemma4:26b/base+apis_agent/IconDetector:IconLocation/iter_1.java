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
import java.util.List;

public class IconDetector extends Detector implements XmlScanner {

    private static final String ID = "ImageInDensityIndependentDrawableFolder";
    private
            static final String SHORT_DESC = "Bitmap in density-independent folder";
    private static final String LONG_DESC = "Bitmaps should be placed in density-specific drawable folders (e.g., drawable-hdpi) or drawable-nodpi, rather than the generic res/drawable folder.";

    private static final List<String> ATTRIBUTES_TO_CHECK = Arrays.asList(
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

    private static final List<String> BITMAP_EXTENSIONS = Arrays.asList(".png", ".webp", ".jpg", ".jpeg");

    public static final Issue ISSUE = Issue.create(
            ID,
            SHORT_DESC,
            LONG_DESC,
            BugCategory.CORRECTNESS,
            10,
            Severity.WARNING,
            new Issue.Implementation(new IconDetector(), null)
    );

    @Override
    public Collection<String> getApplicableAttributes() {
        return ATTRIBUTES_TO_CHECK;
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return true;
    }

    @Override
    public void visitAttribute(XmlContext context, Element element, Attr attribute) {
        String value = attribute.getValue();
        if (value == null || !value.startsWith("@drawable/")) {
            return;
        }

        String resourceName = value.substring(9);

        for (String extension : BITMAP_EXTENSIONS) {
            try {
                // We check if the file with this extension exists specifically in the base 'res/drawable' folder.
                // If getResourceFolderType returns ResourceFolderType.Drawable, it is in the generic folder.
                ResourceFolderType folderType = context.getEvaluationContext().getResourceFolderType(
                        resourceName + extension,
                        ResourceType.drawable
                );

                if (folderType == ResourceFolderType.Drawable) {
                    context.report(
                            ISSUE,
                            attribute,
                            context.getLocation(attribute),
                            "Found bitmap '" + resourceName + extension + "' in the generic res/drawable folder."
                    );
                    break;
                }
            } catch (Exception e) {
                // Ignore errors during resource resolution probes
            }
        }
    }
}