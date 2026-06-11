package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.llint.detector.api.Severity;

import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

public class IconDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "NotificationIconCompatibility",
            "Notification icons should define a raster image to support Android versions below 5.0 (API 21).",
            "Notification icons that do not include a raster image will not be displayed correctly on devices running Android versions below 5.0.",
            Category.USABILITY,
            6, // Priority
            Severity.WARNING,
            new Implementation(IconDetector.class, true));

    @Override
    public List<ResourceFolderType> getApplicableResourceFolders() {
        return Collections.singletonList(ResourceFolderType.DRAWABLE);
    }

    @Override
    public void visitElement(@NonNull Element element, @NonNull Location location) {
        String name = location.getFullyQualifiedName();
        if (name != null && name.startsWith("ic_stat_")) {
            // Check for vector drawable without a raster fallback
            boolean isVectorDrawable = "vector".equals(element.getAttribute("xmlns:android"));
            if (isVectorDrawable) {
                context.report(ISSUE, location, "Notification icon should define a raster image to support Android versions below 5.0.");
            }
        }
    }

}