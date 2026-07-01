package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.UElementHandler;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue CONVERT_TO_WEBP =
            Issue.create(
                    "ConvertToWebp",
                    "Lossless image format",
                    "The WebP format is typically more compact than PNG and JPEG. As of "
                            + "Android 4.2.1 it supports transparency and lossless conversion "
                            + "as well. Note that there is a quickfix in the IDE which lets you "
                            + "perform conversion. Previously, launcher icons were required to "
                            + "be in the PNG format but that restriction is no longer there, so "
                            + "lint now flags these.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            IconDetector.class,
                            EnumSet.of(
                                    Scope.RESOURCE_FILE_SCOPE,
                                    Scope.JAVA_FILE_SCOPE,
                                    Scope.MANIFEST_SCOPE)));

    private static final String ANDROID_URI =
            "http://schemas.android.com/apk/res/android";

    private static final Set<String> NON_WEBP_EXTENSIONS = new HashSet<>();

    static {
        NON_WEBP_EXTENSIONS.add("png");
        NON_WEBP_EXTENSIONS.add("jpg");
        NON_WEBP_EXTENSIONS.add("jpeg");
        NON_WEBP_EXTENSIONS.add("gif");
        NON_WEBP_EXTENSIONS.add("bmp");
    }

    private final Set<String> mReferencedIcons = new HashSet<>();

    @Override
    public void beforeCheckRootProject(Context context) {
        mReferencedIcons.clear();
    }

    @Override
    public void afterCheckEachProject(Context context) {
        Project project = context.getProject();
        for (File resDir : project.getResourceFolders()) {
            if (resDir == null || !resDir.isDirectory()) {
                continue;
            }
            File[] typeDirs = resDir.listFiles();
            if (typeDirs == null) {
                continue;
            }
            for (File typeDir : typeDirs) {
                String dirName = typeDir.getName();
                if (!dirName.startsWith("drawable") && !dirName.startsWith("mipmap")) {
                    continue;
                }
                File[] files = typeDir.listFiles();
                if (files == null) {
                    continue;
                }
                for (File file : files) {
                    if (!file.isFile()) {
                        continue;
                    }
                    String fileName = file.getName();
                    if (fileName.endsWith(".9.png")) {
                        continue;
                    }
                    String ext = getExtension(fileName);
                    if (NON_WEBP_EXTENSIONS.contains(ext)) {
                        Location location = Location.create(file);
                        context.report(
                                CONVERT_TO_WEBP,
                                location,
                                "This image can typically be converted to the more compact WebP format.");
                    }
                }
            }
        }
    }

    private static String getExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase();
    }

    @Override
    public boolean filterIncident(
            Context context,
            Issue issue,
            Severity severity,
            Location location,
            String message,
            Object startData,
            Object endData) {
        return issue == CONVERT_TO_WEBP;
    }

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.DRAWABLE
                || folderType == com.android.resources.ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                "application",
                "activity",
                "activity-alias",
                "provider",
                "receiver",
                "service",
                "bitmap",
                "nine-patch",
                "item");
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        String tag = element.getTagName();
        String[] attributes;
        if ("application".equals(tag)
                || "activity".equals(tag)
                || "activity-alias".equals(tag)
                || "provider".equals(tag)
                || "receiver".equals(tag)
                || "service".equals(tag)) {
            attributes = new String[] {"icon", "logo"};
        } else {
            attributes = new String[] {"src", "drawable"};
        }
        for (String attribute : attributes) {
            String value = element.getAttributeNS(ANDROID_URI, attribute);
            if (value != null && !value.isEmpty()) {
                recordReference(value);
            }
        }
    }

    private void recordReference(String value) {
        if (value.startsWith("@drawable/") || value.startsWith("@mipmap/")) {
            int slash = value.indexOf('/');
            int colon = value.indexOf(':');
            int start = colon != -1 && colon > slash ? colon + 1 : slash + 1;
            mReferencedIcons.add(value.substring(start));
        } else if (value.startsWith("R.drawable.") || value.startsWith("R.mipmap.")) {
            int slash = value.lastIndexOf('.');
            if (slash != -1) {
                mReferencedIcons.add(value.substring(slash + 1));
            }
        }
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(UMethod node) {
                // No method-level analysis required for WebP conversion.
            }

            @Override
            public void visitCallExpression(UCallExpression node) {
                String name = node.getMethodName();
                if ("setImageResource".equals(name)
                        || "setImageDrawable".equals(name)
                        || "setImageBitmap".equals(name)
                        || "getDrawable".equals(name)
                        || "getDrawableForDensity".equals(name)) {
                    for (UExpression arg : node.getValueArguments()) {
                        if (arg instanceof USimpleNameReferenceExpression) {
                            visitSimpleNameReferenceExpression(
                                    (USimpleNameReferenceExpression) arg);
                        }
                    }
                }
            }

            @Override
            public void visitClass(UClass node) {
                // No class-level analysis required for WebP conversion.
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    USimpleNameReferenceExpression node) {
                String identifier = node.getIdentifier();
                if (identifier != null) {
                    recordReference(identifier);
                }
            }
        };
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        List<Class<? extends UElement>> types = new ArrayList<>(4);
        types.add(UCallExpression.class);
        types.add(UClass.class);
        types.add(USimpleNameReferenceExpression.class);
        types.add(UMethod.class);
        return types;
    }
}