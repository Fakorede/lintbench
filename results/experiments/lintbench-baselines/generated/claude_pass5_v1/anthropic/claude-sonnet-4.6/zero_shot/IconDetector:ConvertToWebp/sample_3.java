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
 * Checks for image files that could be converted to WebP format for better compression.
 */
public class IconDetector extends ResourceXmlDetector {

    /** Issue: Convert images to WebP format */
    public static final Issue WEBP_ELIGIBLE = Issue.create(
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
                    EnumSet.of(Scope.ALL_RESOURCE_FILES)
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
    @Nullable
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        File file = context.file;
        String name = file.getName();
        if (isEligibleForWebp(name)) {
            // Check that it's not a nine-patch
            if (!name.endsWith(".9.png")) {
                String message = "One or more images in this project can be converted to "
                        + "the WebP format which typically results in smaller file sizes, "
                        + "even for lossless conversion";
                Location location = Location.create(file);
                context.report(WEBP_ELIGIBLE, location, message);
            }
        }
    }

    /**
     * Returns true if the given file name is eligible for WebP conversion.
     *
     * @param name the file name to check
     * @return true if the file can potentially be converted to WebP
     */
    private static boolean isEligibleForWebp(@NonNull String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".png")) {
            // Nine-patch images cannot be converted
            if (lower.endsWith(".9.png")) {
                return false;
            }
            return true;
        } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return true;
        }
        return false;
    }
}