package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.SdkConstants;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String BITMAP_QNAME = "android.graphics.Bitmap";
    private static final String CREATE_SCALED_BITMAP = "createScaledBitmap";
    private static final int[] EXPECTED_LAUNCHER_SIZES = {
            36, // ldpi
            48, // mdpi
            72, // hdpi
            96, // xhdpi
            144, // xxhdpi
            192 // xxxhdpi
    };

    private static final Implementation IMPLEMENTATION =
            new Implementation(IconDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "IconExpectedSize",
                    "Icon has incorrect size",
                    "There are predefined sizes (for each density) for launcher icons. You should follow these conventions to make sure your icons fit in with the overall look of the platform.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // No project-wide state needs to be reset.
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // No additional project-wide verification required.
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("bitmap", "application");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if ("bitmap".equals(tag)) {
            checkBitmapElement(context, element);
        } else if ("application".equals(tag)) {
            checkApplicationElement(context, element);
        }
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // No class-level icon-size checks are performed.
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UMethod.class, UCallExpression.class, USimpleNameReferenceExpression.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {
                // No method-level checks.
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                checkScaledBitmapCall(context, node);
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                // No standalone reference checks.
            }
        };
    }

    private void checkScaledBitmapCall(JavaContext context, UCallExpression node) {
        if (!CREATE_SCALED_BITMAP.equals(node.getMethodName())) {
            return;
        }

        JavaEvaluator evaluator = context.getEvaluator();
        PsiMethod method = evaluator.resolve(node);
        if (method == null) {
            return;
        }

        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null || !BITMAP_QNAME.equals(containingClass.getQualifiedName())) {
            return;
        }

        List<UExpression> args = node.getValueArguments();
        if (args.size() < 2) {
            return;
        }

        Object widthValue = ConstantEvaluator.evaluate(context, args.get(0));
        Object heightValue = ConstantEvaluator.evaluate(context, args.get(1));
        if (!(widthValue instanceof Integer) || !(heightValue instanceof Integer)) {
            return;
        }

        int width = (Integer) widthValue;
        int height = (Integer) heightValue;
        if (!isExpectedLauncherSize(width, height)) {
            String message =
                    String.format(
                            "Expected launcher icon sizes are 36x36, 48x48, 72x72, 96x96, 144x144, or 192x192; found %dx%d",
                            width, height);
            context.report(new Incident(ISSUE, context.getLocation(node), message));
        }
    }

    private void checkBitmapElement(XmlContext context, Element element) {
        String src = element.getAttributeNS(SdkConstants.ANDROID_URI, "src");
        if (src != null && !src.isEmpty() && (src.contains("ic_launcher") || src.contains("launcher"))) {
            String message =
                    "Ensure launcher icon bitmaps follow the expected sizes for each density";
            context.report(new Incident(ISSUE, context.getLocation(element), message));
        }
    }

    private void checkApplicationElement(XmlContext context, Element element) {
        String icon = element.getAttributeNS(SdkConstants.ANDROID_URI, "icon");
        if (icon != null && !icon.isEmpty() && !icon.startsWith("@mipmap/")) {
            String message =
                    "Launcher icons should be placed in the mipmap folders and follow the expected sizes for each density";
            context.report(new Incident(ISSUE, context.getLocation(element), message));
        }
    }

    private static boolean isExpectedLauncherSize(int width, int height) {
        return width == height && Arrays.binarySearch(EXPECTED_LAUNCHER_SIZES, width) >= 0;
    }
}