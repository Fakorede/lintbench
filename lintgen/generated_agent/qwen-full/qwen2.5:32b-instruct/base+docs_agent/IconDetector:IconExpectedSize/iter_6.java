package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.w3c.dom.Element;
import org.xml.sax.Locator;

import java.util.HashMap;
import java.util.Map;

public class IconDetector extends ResourceXmlDetector {

    private static final Map<Density, Integer> LAUNCHER_ICON_SIZES = new HashMap<>();
    static {
        LAUNCHER_ICON_SIZES.put(Density.MDPI, 48);
        LAUNCHER_ICON_SIZES.put(Density.HDPI, 72);
        LAUNCHER_ICON_SIZES.put(Density.XHDPI, 96);
        LAUNCHER_ICON_SIZES.put(Density.XXHDPI, 144);
        LAUNCHER_ICON_SIZES.put(Density.XXXHDPI, 192);
    }

    public static final Issue ISSUE = Issue.create(
            "IncorrectLauncherIconSize",
            "The launcher icon has an incorrect size for the specified density.",
            "Launcher icons should follow predefined sizes to fit in with the overall look of the platform. " +
                    "Using incorrect sizes can lead to distorted or pixelated icons on different devices.",
            Category.USABILITY,
            5, // Priority
            Severity.WARNING,
            new Implementation(
                    IconDetector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public void visitResource(@NonNull ResourceFolderType folderType, @NonNull String resourcePath, @NonNull Context context) {
        if (folderType == ResourceFolderType.DRAWABLE && isLauncherIcon(resourcePath)) {
            checkIconSize(context);
        }
    }

    private boolean isLauncherIcon(@NonNull String resourcePath) {
        return resourcePath.contains("ic_launcher");
    }

    private void checkIconSize(@NonNull Context context) {
        Element element = context.getEvent().getElement();
        if (element == null) {
            return;
        }

        String densityAttr = element.getAttribute("srcDensity");
        Density density = Density.parse(densityAttr);
        if (density == null) {
            return;
        }

        int width = Integer.parseInt(element.getAttribute("width"));
        int height = Integer.parseInt(element.getAttribute("height"));

        if (!LAUNCHER_ICON_SIZES.getOrDefault(density, -1).equals(width)
                || !LAUNCHER_ICON_SIZES.getOrDefault(density, -1).equals(height)) {
            reportIssue(context, element);
        }
    }

    private void reportIssue(@NonNull Context context, @NonNull Element element) {
        String message = "Launcher icon has incorrect size for density: "
                + Density.parse(element.getAttribute("srcDensity")).toString();
        Locator locator = context.getDriver().getXmlParserContext().getLocator();
        if (locator != null) {
            int line = locator.getLineNumber();
            report(ISSUE, context.getLocation(line), message);
        }
    }
}