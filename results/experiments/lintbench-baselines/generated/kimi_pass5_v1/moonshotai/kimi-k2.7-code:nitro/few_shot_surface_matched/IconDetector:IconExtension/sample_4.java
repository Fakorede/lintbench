package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintDriver;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.TextFormat;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "IconExtension",
                    "Icon format does not match the file extension",
                    "Ensures that icons have the correct file extension (e.g. a `.png` file is"
                            + " really in the PNG format and not for example a GIF file named"
                            + " `.png`).",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            IconDetector.class,
                            EnumSet.of(Scope.JAVA_FILE_SCOPE, Scope.RESOURCE_FILE_SCOPE),
                            EnumSet.noneOf(Scope.class)));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String[] ICON_ATTRIBUTES = {"src", "icon", "logo", "drawable", "background"};

    private final Map<String, Set<String>> mReferencedIcons = new HashMap<>();
    private final Set<File> mReportedFiles = new HashSet<>();

    @Override
    public boolean appliesTo(Context context, File file) {
        String parent = file.getParent();
        if (parent == null) {
            return false;
        }
        String folder = new File(parent).getName();
        return (folder.startsWith("drawable") || folder.startsWith("mipmap")) && isImageFile(file);
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        mReferencedIcons.clear();
        mReportedFiles.clear();
    }

    @Override
    public void afterCheckEachProject(Context context) {
        checkReferencedIcons(context);
        mReferencedIcons.clear();
    }

    @Override
    public boolean filterIncident(
            LintDriver driver, Context context, Incident incident, TextFormat format) {
        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                "application",
                "activity",
                "activity-alias",
                "service",
                "receiver",
                "provider",
                "shortcut",
                "ImageView",
                "ImageButton",
                "bitmap",
                "item");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        for (String attr : ICON_ATTRIBUTES) {
            String value = element.getAttributeNS(ANDROID_URI, attr);
            if (value.isEmpty()) {
                value = element.getAttribute("android:" + attr);
            }
            recordReference(value);
        }
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(UMethod node) {
                IconDetector.this.visitMethod(node);
            }

            @Override
            public void visitCallExpression(UCallExpression node) {
                IconDetector.this.visitCallExpression(node);
            }

            @Override
            public void visitClass(UClass node) {
                IconDetector.this.visitClass(node);
            }

            @Override
            public void visitSimpleNameReferenceExpression(USimpleNameReferenceExpression node) {
                IconDetector.this.visitSimpleNameReferenceExpression(node);
            }
        };
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UMethod.class, UCallExpression.class, UClass.class, USimpleNameReferenceExpression.class);
    }

    public void visitMethod(UMethod node) {}

    public void visitCallExpression(UCallExpression node) {
        String name = node.getMethodName();
        if (name == null) {
            return;
        }
        if (name.equals("setImageResource")
                || name.equals("setBackgroundResource")
                || name.equals("setImageDrawable")
                || name.equals("setIcon")
                || name.equals("setLogo")) {
            List<UExpression> args = node.getValueArguments();
            if (!args.isEmpty() && args.get(0) instanceof USimpleNameReferenceExpression) {
                recordReference((USimpleNameReferenceExpression) args.get(0));
            }
        }
    }

    public void visitClass(UClass node) {}

    public void visitSimpleNameReferenceExpression(USimpleNameReferenceExpression node) {
        recordReference(node);
    }

    private void recordReference(USimpleNameReferenceExpression node) {
        PsiElement resolved = node.resolve();
        if (!(resolved instanceof PsiField)) {
            return;
        }
        PsiField field = (PsiField) resolved;
        PsiClass cls = field.getContainingClass();
        if (cls == null) {
            return;
        }
        String type = cls.getName();
        PsiClass parent = cls.getContainingClass();
        if (parent != null
                && "R".equals(parent.getName())
                && ("drawable".equals(type) || "mipmap".equals(type))) {
            recordIcon(type, field.getName());
        }
    }

    private void recordReference(String value) {
        if (value.startsWith("@drawable/")) {
            recordIcon("drawable", value.substring("@drawable/".length()));
        } else if (value.startsWith("@mipmap/")) {
            recordIcon("mipmap", value.substring("@mipmap/".length()));
        }
    }

    private void recordIcon(String type, String name) {
        mReferencedIcons.computeIfAbsent(type, k -> new HashSet<>()).add(name);
    }

    private void checkReferencedIcons(Context context) {
        Project project = context.getProject();
        List<File> resourceFolders = project.getResourceFolders();
        for (Map.Entry<String, Set<String>> entry : mReferencedIcons.entrySet()) {
            String type = entry.getKey();
            for (String name : entry.getValue()) {
                for (File resDir : resourceFolders) {
                    File[] dirs = resDir.listFiles();
                    if (dirs == null) {
                        continue;
                    }
                    for (File dir : dirs) {
                        if (dir.isDirectory() && dir.getName().startsWith(type)) {
                            File[] files = dir.listFiles();
                            if (files == null) {
                                continue;
                            }
                            for (File file : files) {
                                if (file.isFile() && getBaseName(file.getName()).equals(name)) {
                                    checkImageFile(context, file);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void checkImageFile(Context context, File file) {
        if (!isImageFile(file) || !mReportedFiles.add(file)) {
            return;
        }
        String extension = getExtension(file.getName());
        String actual = detectFormat(file);
        if (actual == null) {
            return;
        }
        String expected = extension;
        if (expected.equals("jpeg")) {
            expected = "jpg";
        } else if (expected.equals("9.png")) {
            expected = "png";
        }
        if (!expected.equals(actual)) {
            String message =
                    "Icon format does not match the file extension: file is a "
                            + actual
                            + " but uses extension ."
                            + extension;
            context.report(ISSUE, Location.create(file), message);
        }
    }

    private static boolean isImageFile(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".png")
                || name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".gif")
                || name.endsWith(".bmp")
                || name.endsWith(".webp")
                || name.endsWith(".9.png");
    }

    private static String getExtension(String fileName) {
        if (fileName.endsWith(".9.png")) {
            return "9.png";
        }
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private static String getBaseName(String fileName) {
        if (fileName.endsWith(".9.png")) {
            return fileName.substring(0, fileName.length() - ".9.png".length());
        }
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static String detectFormat(File file) {
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
                return "png";
            }
            if (header[0] == 'G' && header[1] == 'I' && header[2] == 'F') {
                return "gif";
            }
            if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8) {
                return "jpg";
            }
            if (header[0] == 'B' && header[1] == 'M') {
                return "bmp";
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
                return "webp";
            }
            return null;
        } catch (IOException e) {
            return null;
        }
    }
}