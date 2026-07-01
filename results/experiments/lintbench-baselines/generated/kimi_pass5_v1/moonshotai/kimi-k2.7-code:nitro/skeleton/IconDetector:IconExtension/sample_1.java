package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.client.api.UElementHandler;
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
import com.android.tools.lint.detector.api.XmlContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(IconDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "IconExtension",
                    "Icon format does not match the file extension",
                    "Icon files should use a file extension that matches their actual image format. "
                            + "For example, a file named with a `.png` extension must really be a PNG "
                            + "image, not a GIF, JPEG, WebP, or other format renamed to `.png`.",
                    Category.ICONS,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final byte[] PNG_MAGIC = new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };
    private static final byte[] GIF_MAGIC_87 = new byte[] {
            0x47, 0x49, 0x46, 0x38, 0x37, 0x61
    };
    private static final byte[] GIF_MAGIC_89 = new byte[] {
            0x47, 0x49, 0x46, 0x38, 0x39, 0x61
    };
    private static final byte[] BMP_MAGIC = new byte[] {
            0x42, 0x4D
    };
    private static final byte[] RIFF_MAGIC = new byte[] {
            0x52, 0x49, 0x46, 0x46
    };
    private static final byte[] WEBP_MAGIC = new byte[] {
            0x57, 0x45, 0x42, 0x50
    };

    private final Set<String> mReported = new HashSet<>();

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mReported.clear();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        Project project = context.getProject();
        List<File> resourceFolders = project.getResourceFolders();
        if (resourceFolders == null) {
            return;
        }
        for (File resFolder : resourceFolders) {
            File[] typeDirs = resFolder.listFiles();
            if (typeDirs == null) {
                continue;
            }
            for (File typeDir : typeDirs) {
                String dirName = typeDir.getName();
                if (typeDir.isDirectory()
                        && (dirName.startsWith("drawable") || dirName.startsWith("mipmap"))) {
                    File[] files = typeDir.listFiles();
                    if (files == null) {
                        continue;
                    }
                    for (File file : files) {
                        if (file.isFile()) {
                            checkIconFile(context, file);
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return false;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Not needed for the IconExtension check.
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Not needed for the IconExtension check.
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.emptyList();
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {
                // Not needed for the IconExtension check.
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                // Not needed for the IconExtension check.
            }

            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                // Not needed for the IconExtension check.
            }
        };
    }

    private void checkIconFile(@NonNull Context context, @NonNull File file) {
        String path = file.getAbsolutePath();
        if (mReported.contains(path)) {
            return;
        }

        String extension = getExtension(file.getName());
        if (extension == null) {
            return;
        }

        String expectedFormat = getExpectedFormat(extension);
        if (expectedFormat == null) {
            return;
        }

        String actualFormat = getActualFormat(file);
        if (actualFormat == null) {
            return;
        }

        if (!actualFormat.equals(expectedFormat)) {
            String message = "The following icon file looks like a "
                    + actualFormat
                    + " file but has the ."
                    + extension
                    + " extension";
            Location location = Location.create(file);
            context.report(new Incident(ISSUE, location, message));
            mReported.add(path);
        }
    }

    private String getExtension(@NonNull String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot == -1 || dot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dot + 1).toLowerCase();
    }

    private String getExpectedFormat(@NonNull String extension) {
        switch (extension) {
            case "png":
                return "PNG";
            case "jpg":
            case "jpeg":
                return "JPEG";
            case "gif":
                return "GIF";
            case "webp":
                return "WEBP";
            case "bmp":
                return "BMP";
            default:
                return null;
        }
    }

    private String getActualFormat(@NonNull File file) {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] header = new byte[12];
            int read = in.read(header);
            if (read < 2) {
                return null;
            }

            if (matches(header, PNG_MAGIC)) {
                return "PNG";
            }
            if (matches(header, GIF_MAGIC_87) || matches(header, GIF_MAGIC_89)) {
                return "GIF";
            }
            if (read >= 3
                    && (header[0] & 0xFF) == 0xFF
                    && (header[1] & 0xFF) == 0xD8
                    && (header[2] & 0xFF) == 0xFF) {
                return "JPEG";
            }
            if (matches(header, BMP_MAGIC)) {
                return "BMP";
            }
            if (read >= 12 && matches(header, RIFF_MAGIC) && matchesAt(header, 8, WEBP_MAGIC)) {
                return "WEBP";
            }

            return null;
        } catch (IOException e) {
            return null;
        }
    }

    private static boolean matches(@NonNull byte[] data, @NonNull byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesAt(
            @NonNull byte[] data, int offset, @NonNull byte[] prefix) {
        if (data.length < offset + prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[offset + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}