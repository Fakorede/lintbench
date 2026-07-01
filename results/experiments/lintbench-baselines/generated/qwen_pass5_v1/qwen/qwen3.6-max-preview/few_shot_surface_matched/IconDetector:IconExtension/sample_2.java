package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.USimpleNameReferenceExpression;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "IconExtension",
            "Icon format does not match the file extension",
            "Ensures that icons have the correct file extension (e.g. a `.png` file is "
                    + "really in the PNG format and not for example a GIF file named `.png`).",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.JAVA_AND_RESOURCE_FILES));

    @Override
    public void beforeCheckRootProject(@com.android.annotations.NonNull Context context) {
    }

    @Override
    public void afterCheckEachProject(@com.android.annotations.NonNull Context context) {
    }

    @Override
    public boolean filterIncident(@com.android.annotations.NonNull Incident incident) {
        return true;
    }

    @Override
    public boolean appliesTo(@com.android.annotations.NonNull com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.DRAWABLE
                || folderType == com.android.resources.ResourceFolderType.MIPMAP;
    }

    @com.android.annotations.Nullable
    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList("ImageView");
    }

    @Override
    public void visitElement(@com.android.annotations.NonNull XmlContext context, @com.android.annotations.NonNull org.w3c.dom.Element element) {
        String src = element.getAttribute("android:src");
        if (src != null && !src.isEmpty()) {
            checkExtension(context, element, src);
        }
    }

    @com.android.annotations.Nullable
    @Override
    public UElementHandler createUastHandler() {
        return new UElementHandler() {
            @Override
            public void visitClass(@com.android.annotations.NonNull UClass node) {
            }

            @Override
            public void visitCallExpression(@com.android.annotations.NonNull UCallExpression node) {
            }

            @Override
            public void visitSimpleNameReferenceExpression(@com.android.annotations.NonNull USimpleNameReferenceExpression node) {
            }
        };
    }

    @Override
    public void visitMethod(@com.android.annotations.NonNull JavaContext context, @com.android.annotations.NonNull UCallExpression call, @com.android.annotations.NonNull PsiMethod method) {
    }

    @Override
    public void visitCallExpression(@com.android.annotations.NonNull JavaContext context, @com.android.annotations.NonNull UCallExpression call) {
        String methodName = call.getMethodName();
        if (methodName != null && (methodName.contains("Icon") || methodName.contains("Image"))) {
            java.util.List<org.jetbrains.uast.UExpression> args = call.getValueArguments();
            if (!args.isEmpty()) {
                String value = args.get(0).asSourceString();
                if (value != null) {
                    checkExtension(context, call, value);
                }
            }
        }
    }

    @Override
    public void visitClass(@com.android.annotations.NonNull JavaContext context, @com.android.annotations.NonNull UClass declaration) {
    }

    @Override
    public void visitSimpleNameReferenceExpression(@com.android.annotations.NonNull JavaContext context, @com.android.annotations.NonNull USimpleNameReferenceExpression reference) {
        String name = reference.getIdentifier();
        if (name != null && (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".gif") || name.endsWith(".webp"))) {
            checkExtension(context, reference, name);
        }
    }

    @com.android.annotations.Nullable
    @Override
    public java.util.List<Class<? extends UElement>> getApplicableUastTypes() {
        return java.util.Collections.singletonList(UClass.class);
    }

    private void checkExtension(@com.android.annotations.NonNull Context context, @com.android.annotations.NonNull Object location, @com.android.annotations.NonNull String name) {
        boolean hasPngExt = name.endsWith(".png");
        boolean hasJpgExt = name.endsWith(".jpg") || name.endsWith(".jpeg");
        boolean hasGifExt = name.endsWith(".gif");
        boolean hasWebpExt = name.endsWith(".webp");

        if (hasPngExt || hasJpgExt || hasGifExt || hasWebpExt) {
            context.report(ISSUE, location, context.getLocation(location),
                    "Icon format does not match the file extension");
        }
    }
}