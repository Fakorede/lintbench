package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "IconExtension",
                    "Icon format does not match the file extension",
                    "Ensures that icons have the correct file extension (e.g. a `.png` file is"
                            + " really in the PNG format and not, for example, a GIF file named"
                            + " `.png`).",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            IconDetector.class,
                            Scope.RESOURCE_FILE_SCOPE,
                            Scope.JAVA_FILE_SCOPE));

    private final List<File> mIconFiles = new ArrayList<>();

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        mIconFiles.clear();
        for (Project project : context.getDriver().getProjects()) {
            List<File> resourceFolders = project.getResourceFolders();
            if (resourceFolders == null) {
                continue;
            }
            for (File res : resourceFolders) {
                collectIcons(res);
            }
        }
    }

    private void collectIcons(File resDir) {
        File[] dirs = resDir.listFiles();
        if (dirs == null) {
            return;
        }
        for (File dir : dirs) {
            String dirName = dir.getName();
            if (dirName.startsWith("drawable") || dirName.startsWith("mipmap")) {
                File[] files = dir.listFiles();
                if (files == null) {
                    continue;
                }
                for (File file : files) {
                    if (file.isFile() && hasImageExtension(file.getName())) {
                        mIconFiles.add(file);
                    }
                }
            }
        }
    }

    private boolean hasImageExtension(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".9.png")
                || lower.endsWith(".png")
                || lower.endsWith(".gif")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".webp")
                || lower.endsWith(".bmp");
    }

    @Override
    public void afterCheckEachProject(Context context) {
        File projectDir = context.getProject().getDir();
        for (File file : mIconFiles) {
            if (!isInProject(file, projectDir)) {
                continue;
            }

            String format = getImageFormat(file);
            if (format == null) {
                continue;
            }

            String expectedExtension = extensionForFormat(format);
            if (expectedExtension == null) {
                continue;
            }

            String currentExtension = getCurrentExtension(file.getName());
            boolean matches =
                    currentExtension.equalsIgnoreCase(expectedExtension)
                            || (format.equals("JPEG")
                                    && (currentExtension.equalsIgnoreCase(".jpg")
                                            || currentExtension.equalsIgnoreCase(".jpeg")));

            if (!matches) {
                String message =
                        "The file extension of this icon (`"
                                + file.getName()
                                + "`) does not match the actual image format ("
                                + format
                                + ").";
                context.report(ISSUE, Location.create(file), message);
            }
        }
    }

    private boolean isInProject(File file, File projectDir) {
        return file.getAbsolutePath()
                .startsWith(projectDir.getAbsolutePath() + File.separator);
    }

    private String getCurrentExtension(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".9.png")) {
            return ".png";
        }
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot).toLowerCase() : "";
    }

    private String getImageFormat(File file) {
        try (InputStream stream = new FileInputStream(file)) {
            byte[] header = new byte[12];
            int read = stream.read(header);
            if (read < 4) {
                return null;
            }

            if (header[0] == (byte) 0x89
                    && header[1] == 'P'
                    && header[2] == 'N'
                    && header[3] == 'G') {
                return "PNG";
            }

            if (header[0] == 'G'
                    && header[1] == 'I'
                    && header[2] == 'F'
                    && header[3] == '8'
                    && (header[4] == '7' || header[4] == '9')
                    && header[5] == 'a') {
                return "GIF";
            }

            if ((header[0] & 0xFF) == 0xFF
                    && (header[1] & 0xFF) == 0xD8
                    && (header[2] & 0xFF) == 0xFF) {
                return "JPEG";
            }

            if (read >= 12
                    && header[0] == 'R'
                    && header[1] == 'I'
                    && header[2] == 'F'
                    && header[3] == 'F'
                    && header[8] == 'W'
                    && header[9] == 'E'
                    && header[10] == 'B'
                    && header[11] == 'P') {
                return "WebP";
            }

            if (header[0] == 'B' && header[1] == 'M') {
                return "BMP";
            }
        } catch (IOException ignored) {
        }

        return null;
    }

    private String extensionForFormat(String format) {
        switch (format) {
            case "PNG":
                return ".png";
            case "GIF":
                return ".gif";
            case "JPEG":
                return ".jpg";
            case "WebP":
                return ".webp";
            case "BMP":
                return ".bmp";
            default:
                return null;
        }
    }

    @Override
    public boolean filterIncident(Context context, Incident incident, LintMap scope) {
        return true;
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return null;
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(UMethod node) {
            }

            @Override
            public void visitCallExpression(UCallExpression node) {
            }

            @Override
            public void visitClass(UClass node) {
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    USimpleNameReferenceExpression node) {
            }
        };
    }
}