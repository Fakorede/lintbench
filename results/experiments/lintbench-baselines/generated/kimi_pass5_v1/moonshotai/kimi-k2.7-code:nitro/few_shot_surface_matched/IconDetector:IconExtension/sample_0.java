package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_BACKGROUND;
import static com.android.SdkConstants.ATTR_ICON;
import static com.android.SdkConstants.ATTR_LOGO;
import static com.android.SdkConstants.ATTR_SRC;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.xml.AndroidManifest.NODE_ACTIVITY;
import static com.android.xml.AndroidManifest.NODE_ACTIVITY_ALIAS;
import static com.android.xml.AndroidManifest.NODE_APPLICATION;
import static com.android.xml.AndroidManifest.NODE_PROVIDER;
import static com.android.xml.AndroidManifest.NODE_RECEIVER;
import static com.android.xml.AndroidManifest.NODE_SERVICE;

import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.UElementHandler;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

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

    private static final int BUFFER_SIZE = 12;
    private static final String DRAWABLE = "drawable";
    private static final String MIPMAP = "mipmap";
    private static final String ANDROID_PKG = "@android:";

    private Set<String> mReportedPaths;

    @Override
    public void beforeCheckRootProject(Context context) {
        mReportedPaths = new HashSet<>();
    }

    @Override
    public void afterCheckEachProject(Context context) {
        mReportedPaths = null;
    }

    @Override
    public boolean filterIncident(
            LintDriver driver, Context context, Incident incident, Object session) {
        return true;
    }

    @Override
    public boolean appliesTo(Context context, File file) {
        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                NODE_APPLICATION,
                NODE_ACTIVITY,
                NODE_ACTIVITY_ALIAS,
                NODE_SERVICE,
                NODE_RECEIVER,
                NODE_PROVIDER,
                TAG_ITEM);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Attr attr = (Attr) attributes.item(i);
            String name = attr.getLocalName();
            if (name == null) {
                continue;
            }
            if (!ATTR_ICON.equals(name)
                    && !ATTR_LOGO.equals(name)
                    && !ATTR_SRC.equals(name)
                    && !ATTR_BACKGROUND.equals(name)) {
                continue;
            }
            String value = attr.getValue();
            if (value.isEmpty() || value.startsWith(ANDROID_PKG) || value.startsWith("?")) {
                continue;
            }
            int slash = value.indexOf('/');
            if (slash <= 0 || slash == value.length() - 1) {
                continue;
            }
            String prefix = value.substring(1, slash);
            if (prefix.contains(":")) {
                int colon = prefix.indexOf(':');
                prefix = prefix.substring(colon + 1);
            }
            String entry = value.substring(slash + 1);
            if (DRAWABLE.equals(prefix) || MIPMAP.equals(prefix)) {
                checkIconName(context, prefix, entry, attr);
            }
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UClass.class, UMethod.class, UCallExpression.class,
                USimpleNameReferenceExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(UClass node) {
                IconDetector.this.visitClass(context, node);
            }

            @Override
            public void visitMethod(UMethod node) {
                PsiMethod method = node.getJavaPsi();
                if (method != null) {
                    IconDetector.this.visitMethod(context, node, method);
                }
            }

            @Override
            public void visitCallExpression(UCallExpression node) {
                IconDetector.this.visitCallExpression(context, node);
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    USimpleNameReferenceExpression node) {
                IconDetector.this.visitSimpleNameReferenceExpression(context, node);
            }
        };
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        // No class-level checks are required for this issue.
    }

    @Override
    public void visitMethod(
            JavaContext context, UMethod node, PsiMethod method) {
        // No method-level checks are required for this issue.
    }

    @Override
    public void visitCallExpression(JavaContext context, UCallExpression node) {
        String methodName = node.getMethodName();
        if (methodName == null) {
            return;
        }
        if (!methodName.endsWith("Resource")
                && !"setIcon".equals(methodName)
                && !"setLogo".equals(methodName)) {
            return;
        }
        for (UExpression arg : node.getValueArguments()) {
            ResourceRef ref = resolveResourceRef(arg);
            if (ref != null) {
                checkIconName(context, ref.type, ref.name, node);
            }
        }
    }

    @Override
    public void visitSimpleNameReferenceExpression(
            JavaContext context, USimpleNameReferenceExpression node) {
        ResourceRef ref = resolveResourceRef(node);
        if (ref != null) {
            checkIconName(context, ref.type, ref.name, node);
        }
    }

    private static class ResourceRef {
        final String type;
        final String name;

        ResourceRef(String type, String name) {
            this.type = type;
            this.name = name;
        }
    }

    private ResourceRef resolveResourceRef(UExpression expression) {
        if (!(expression instanceof UReferenceExpression)) {
            return null;
        }
        PsiElement resolved = ((UReferenceExpression) expression).resolve();
        if (!(resolved instanceof PsiField)) {
            return null;
        }
        PsiField field = (PsiField) resolved;
        PsiClass cls = field.getContainingClass();
        if (cls == null) {
            return null;
        }
        String className = cls.getName();
        if (DRAWABLE.equals(className) || MIPMAP.equals(className)) {
            return new ResourceRef(className, field.getName());
        }
        return null;
    }

    private void checkIconName(
            XmlContext context, String type, String name, Attr attribute) {
        List<File> resourceFolders = context.getProject().getResourceFolders();
        if (resourceFolders == null) {
            return;
        }
        for (File res : resourceFolders) {
            File[] folders = res.listFiles();
            if (folders == null) {
                continue;
            }
            for (File folder : folders) {
                if (!folder.isDirectory() || !folder.getName().startsWith(type)) {
                    continue;
                }
                File[] files = folder.listFiles();
                if (files == null) {
                    continue;
                }
                for (File file : files) {
                    if (file.isDirectory()) {
                        continue;
                    }
                    String fileName = file.getName();
                    int dot = fileName.lastIndexOf('.');
                    if (dot <= 0) {
                        continue;
                    }
                    String base = fileName.substring(0, dot);
                    if (!base.equals(name)) {
                        continue;
                    }
                    checkIconFile(context, attribute, file);
                }
            }
        }
    }

    private void checkIconName(
            JavaContext context, String type, String name, UElement node) {
        List<File> resourceFolders = context.getProject().getResourceFolders();
        if (resourceFolders == null) {
            return;
        }
        for (File res : resourceFolders) {
            File[] folders = res.listFiles();
            if (folders == null) {
                continue;
            }
            for (File folder : folders) {
                if (!folder.isDirectory() || !folder.getName().startsWith(type)) {
                    continue;
                }
                File[] files = folder.listFiles();
                if (files == null) {
                    continue;
                }
                for (File file : files) {
                    if (file.isDirectory()) {
                        continue;
                    }
                    String fileName = file.getName();
                    int dot = fileName.lastIndexOf('.');
                    if (dot <= 0) {
                        continue;
                    }
                    String base = fileName.substring(0, dot);
                    if (!base.equals(name)) {
                        continue;
                    }
                    checkIconFile(context, node, file);
                }
            }
        }
    }

    private void checkIconFile(XmlContext context, Attr attribute, File file) {
        String actualFormat = getImageFormat(file);
        if (actualFormat == null) {
            return;
        }
        String extension = getExtension(file);
        if (matchesExtension(actualFormat, extension)) {
            return;
        }
        if (!mReportedPaths.add(file.getAbsolutePath())) {
            return;
        }
        String message =
                "The icon file `"
                        + file.getName()
                        + "` has a `."
                        + extension
                        + "` extension but appears to be a "
                        + actualFormat.toUpperCase(Locale.US)
                        + " file";
        context.report(ISSUE, attribute, context.getLocation(attribute), message);
    }

    private void checkIconFile(JavaContext context, UElement node, File file) {
        String actualFormat = getImageFormat(file);
        if (actualFormat == null) {
            return;
        }
        String extension = getExtension(file);
        if (matchesExtension(actualFormat, extension)) {
            return;
        }
        if (!mReportedPaths.add(file.getAbsolutePath())) {
            return;
        }
        String message =
                "The icon file `"
                        + file.getName()
                        + "` has a `."
                        + extension
                        + "` extension but appears to be a "
                        + actualFormat.toUpperCase(Locale.US)
                        + " file";
        context.report(ISSUE, node, context.getLocation(node), message);
    }

    private static String getImageFormat(File file) {
        try (FileInputStream stream = new FileInputStream(file)) {
            byte[] header = new byte[BUFFER_SIZE];
            int read = stream.read(header);
            if (read < 2) {
                return null;
            }
            if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8) {
                return "jpg";
            }
            if (read >= 8
                    && header[0] == (byte) 0x89
                    && header[1] == 'P'
                    && header[2] == 'N'
                    && header[3] == 'G') {
                return "png";
            }
            if (read >= 6
                    && header[0] == 'G'
                    && header[1] == 'I'
                    && header[2] == 'F') {
                return "gif";
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
            if (read >= 2 && header[0] == 'B' && header[1] == 'M') {
                return "bmp";
            }
            if (isXml(header, read)) {
                return "xml";
            }
        } catch (IOException e) {
            // Ignore files we cannot read.
        }
        return null;
    }

    private static boolean isXml(byte[] header, int length) {
        if (length < 5) {
            return false;
        }
        String start = new String(header, 0, Math.min(length, 5));
        if (start.equals("<?xml")) {
            return true;
        }
        if (length >= 7) {
            String tag = new String(header, 0, 7);
            if (tag.startsWith("<vector") || tag.startsWith("<nine-p")) {
                return true;
            }
        }
        return false;
    }

    private static String getExtension(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot == -1 ? "" : name.substring(dot + 1).toLowerCase(Locale.US);
    }

    private static boolean matchesExtension(String format, String extension) {
        String ext = extension.toLowerCase(Locale.US);
        if (format.equals(ext)) {
            return true;
        }
        if ("jpg".equals(format) && ("jpeg".equals(ext) || "jpg".equals(ext))) {
            return true;
        }
        if ("jpeg".equals(format) && ("jpeg".equals(ext) || "jpg".equals(ext))) {
            return true;
        }
        return false;
    }
}