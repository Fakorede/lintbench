package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Element;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;

/**
 * Detector which checks for PNG and JPEG images that could be converted to WebP format.
 */
public class IconDetector extends ResourceXmlDetector {

    /** Convert images to WebP */
    public static final Issue ISSUE = Issue.create(
            "ConvertToWebp",
            "Convert to WebP",
            "The WebP format is typically more compact than PNG and JPEG. As of Android 4.2.1 " +
            "it supports transparency and lossless conversion as well. Note that there is a " +
            "quickfix in the IDE which lets you perform conversion.\n" +
            "\n" +
            "Previously, launcher icons were required to be in the PNG format but that " +
            "restriction is no longer there, so lint now flags these.",
            Category.ICONS,
            1,
            Severity.WARNING,
            new Implementation(
                    IconDetector.class,
                    EnumSet.of(Scope.BINARY_RESOURCE_FILE)
            )
    );

    /** Constructs a new {@link IconDetector} */
    public IconDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        File file = context.file;
        String name = file.getName();
        String lowerName = name.toLowerCase();

        if (lowerName.endsWith(".webp")) {
            // Already WebP, nothing to do
            return;
        }

        if (lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
            // Skip nine-patch images
            if (lowerName.endsWith(".9.png")) {
                return;
            }

            // Check minimum API level - WebP with transparency/lossless requires API 18
            // Basic WebP support starts at API 14
            // We'll flag these for conversion
            String message = String.format(
                    "One or more images in this project can be converted to the WebP format " +
                    "which typically results in smaller file sizes, even for lossless conversion. " +
                    "The IDE can perform this conversion. Right-click on the image file, choose " +
                    "\"Convert to WebP\"... (%1$s)",
                    name);

            Location location = Location.create(file);
            context.report(ISSUE, location, message);
        }
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Not used
    }
}