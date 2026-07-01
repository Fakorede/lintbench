package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import org.jetbrains.annotations.NotNull;
import java.io.File;

public class IconDetector extends Detector {
    public static final Issue ISSUE = Issue.create(
            "IconMixedNinePatch",
            "Clashing PNG and 9-PNG files",
            "If you accidentally name two separate resources `file.png` and `file.9.png`, " +
            "the image file and the nine patch file will both map to the same drawable " +
            "resource, `@drawable/file`, which is probably not what was intended.",
            Category.ICONS,
            6,
            Severity.ERROR,
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public void visitFile(@NotNull Context context, @NotNull File file) {
        String name = file.getName();
        if (!name.endsWith(".9.png")) {
            return;
        }

        File parent = file.getParentFile();
        if (parent == null) {
            return;
        }

        String parentName = parent.getName();
        if (!parentName.startsWith("drawable")) {
            return;
        }

        String baseName = name.substring(0, name.length() - 6);
        File pngFile = new File(parent, baseName + ".png");

        if (pngFile.exists()) {
            Location location = Location.create(file);
            Location secondary = Location.create(pngFile);
            location.setSecondary(secondary);
            context.report(ISSUE, location, String.format(
                    "The files `%1$s` and `%2$s` both map to the same drawable resource name `%3$s`",
                    name, pngFile.getName(), baseName));
        }
    }
}