package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.USimpleNameReferenceExpression;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "IconExtension",
                    "Icon format does not match the file extension",
                    "Ensures that icons have the correct file extension (e.g. a `.png` file is "
                            + "really in the PNG format and not, for example, a GIF file named "
                            + "`.png`).",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            IconDetector.class,
                            EnumSet.of(Scope.RESOURCE_FILE_SCOPE, Scope.JAVA_FILE_SCOPE)));

    private static final byte[] PNG_MAGIC =
            new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private final Set<String> mChecked = new HashSet<>();

    @Override
    public void beforeCheckRootProject(Context context) {
        mChecked.clear();
    }

    @Override
    public void afterCheckEachProject(Context context) {
        for (File resDir : context.getProject().getResourceFolders()) {
            if (!resDir.isDirectory()) {
                continue;
            }
            File[] dirs = resDir.listFiles();
            if (dirs == null) {
                continue;
            }
            for (File dir : dirs) {
                String name = dir.getName();
                if (name.startsWith("drawable") || name.startsWith("mipmap")) {
                    scanDirectory(context, dir);
                }
            }
        }
    }

    private void scanDirectory(Context context, File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(context, file);
            } else if (file.isFile()) {
                String path = file.getAbsolutePath();
                if (!mChecked.add(path)) {
                    continue;
                }
                checkIconFile(context, file);
            }
        }
    }

    private void checkIconFile(Context context, File file) {
        String name = file.getName();
        String expectedFormat = getExpectedFormat(name);
        if (expectedFormat == null) {
            return;
        }
        String actualFormat = readActualFormat(file);
        if (actualFormat == null) {
            return;
        }
        if (!expectedFormat.equals(actualFormat)) {
            String extension = getExtension(name);
            String message =
                    "The icon file `"
                            + name
                            + "` appears to be a "
                            + actualFormat
                            + " file but has a ."
                            + extension
                            + " extension";
            context.report(ISSUE, Location.create(file), message);
        }
    }

    private static String getExtension(String name) {
        String lower = name.toLowerCase(Locale.US);
        if (lower.endsWith(".9.png")) {
            return "png";
        }
        int index = lower.lastIndexOf('.');
        return index >= 0 && index < lower.length() - 1 ? lower.substring(index + 1) : "";
    }

    private static String getExpectedFormat(String name) {
        String lower = name.toLowerCase(Locale.US);
        if (lower.endsWith(".png") || lower.endsWith(".9.png")) {
            return "PNG";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "JPEG";
        }
        if (lower.endsWith(".gif")) {
            return "GIF";
        }
        if (lower.endsWith(".webp")) {
            return "WebP";
        }
        if (lower.endsWith(".bmp")) {
            return "BMP";
        }
        if (lower.endsWith(".xml")) {
            return "XML";
        }
        return null;
    }

    private static String readActualFormat(File file) {
        byte[] header = new byte[12];
        try (FileInputStream fis = new FileInputStream(file)) {
            int read = fis.read(header);
            if (read < 4) {
                return null;
            }
            if (startsWith(header, PNG_MAGIC)) {
                return "PNG";
            }
            if (startsWith(header, "GIF87a") || startsWith(header, "GIF89a")) {
                return "GIF";
            }
            if ((header[0] & 0xFF) == 0xFF
                    && (header[1] & 0xFF) == 0xD8
                    && (header[2] & 0xFF) == 0xFF) {
                return "JPEG";
            }
            if (startsWith(header, "RIFF") && startsWith(header, 8, "WEBP")) {
                return "WebP";
            }
            if (startsWith(header, "BM")) {
                return "BMP";
            }
            if (looksLikeXml(header, read)) {
                return "XML";
            }
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        return startsWith(data, 0, prefix);
    }

    private static boolean startsWith(byte[] data, int offset, String s) {
        byte[] bytes = s.getBytes(Locale.US);
        if (data.length < offset + bytes.length) {
            return false;
        }
        for (int i = 0; i < bytes.length; i++) {
            if (