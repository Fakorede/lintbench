package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class IconDetector extends ResourceXmlDetector {

    private Map<String, Collection<String>> mDrawableMap = new HashMap<>();

    @NonNull
    @Override
    public Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList(ResourceFolderType.DRAWABLE.getName());
    }

    @NonNull
    @Override
    public Collection<String> getApplicableAttributes() {
        return java.util.Collections.emptyList();
    }

    @Override
    public void visitResource(@NonNull Context context, @NonNull String resourceType,
                              @NonNull String resourceName, @NonNull String sourcePath) {

        if (resourceName.endsWith(".png") || resourceName.endsWith(".9.png")) {
            // Remove the extension to get the base name
            String baseName = resourceName.substring(0, resourceName.lastIndexOf('.'));

            mDrawableMap.putIfAbsent(baseName, new ArrayList<>());
            mDrawableMap.get(baseName).add(sourcePath);
        }
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        for (String key : mDrawableMap.keySet()) {
            Collection<String> values = mDrawableMap.get(key);
            if (values.size() > 1) {
                boolean hasPng = false;
                boolean hasNinePatch = false;

                for (String path : values) {
                    if (path.endsWith(".png")) {
                        hasPng = true;
                    } else if (path.endsWith(".9.png")) {
                        hasNinePatch = true;
                    }
                }

                if (hasPng && hasNinePatch) {
                    for (String path : values) {
                        Location location = context.getLocation(path);
                        report(context, location, "Clashing PNG and 9-PNG files: " + key);
                    }
                }
            }
        }

        mDrawableMap.clear();
    }

    private void report(Context context, Location location, String message) {
        context.report(IconDetector.ISSUE, location, message);
    }

    public static final Issue ISSUE = Issue.create(
            "ClashingIcons",
            "Clashing PNG and 9-PNG files",
            "If you accidentally name two separate resources `file.png` and `file.9.png`, the image file and the nine patch file will both map to the same drawable resource, `@drawable/file`, which is probably not what was intended.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(IconDetector.class, Scope.MANIFEST_SCOPE)
    );
}