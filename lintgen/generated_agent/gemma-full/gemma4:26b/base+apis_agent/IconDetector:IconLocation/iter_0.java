package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class IconDetector extends Detector implements XmlScanner {

    private static final String ID = "ImageInDensityIndependentDrawableFolder";
    private static final String MESSAGE = "Bitmaps should be placed in density-specific drawable folders (e.g., drawable-hdpi) or drawable-nodpi, rather than the generic res/drawable folder.";

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

    @NonNull
    @Override
    public Collection<String> getApplicableAttributes() {
        return ATTRIBUTES_TO_CHECK;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Element element, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value == null || !value.startsWith("@drawable/")) {
            return;
        }

        String resourceName = value.substring(9);

        for (String extension : BITMAP_EXTENSIONS) {
            // We check if a file with the bitmap extension exists specifically in the base 'res/drawable' folder.
            // In Android, if 'icon.png' exists in 'res/drawable', the resource name is 'icon'.
            // However, we can probe the resource indexer by checking for the existence of the extended name.
            // If getResourceFolderType returns ResourceFolderType.Drawable, it means the file 
            // with that specific extension is located in the density-independent folder.
            try {
                ResourceFolderType folderType = context.getEvaluationContext().getResourceFolderType(
                        resourceName + extension,
                        ResourceType.Drawable
                );

                if (folderType == ResourceFolderType.Drawable) {
                    context.report(
                            Issue.create(ID)
                                    .setShortDescriptionDescription("Bitmap in density-independent folder")
                                    .setLongDescriptionDescription(MESSAGE)
                                    .setSeverity(com.android.tools.lint.detector.api.Severity.WARNING)
                                    .addBugCategory(com.android.tools.lint.detector.api.BugCategory.CORRECTNESS)
                                    .addBugType(com.android.tools.lint.detector.api.BugType.CORRECTNESS)
                                    .addUrl("https://developer.android.com/guide/practices/screens_support.html"),
                            attribute,
                            context.getLocation(attribute),
                            "Found bitmap '" + resourceName + extension + "' in the generic res/drawable folder."
                    );
                    // Once we find one valid bitmap extension in the base folder, no need to check others.
                    break;
                }
            } catch (Exception e) {
                // Ignore errors during resource resolution probes
            }
        }
    }

    @NonNull
    @Override
    public Implementation implementation() {
        return new Implementation(this, null);
    }
}