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
import com.android.tools.lint.detector.api.UElementHandler;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import java.io.File;
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
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ConvertToWebp",
                    "Convert to WebP",
                    "The WebP format is typically more compact than PNG and JPEG. As of Android 4.2.1 "
                            + "it supports transparency and lossless conversion as well. Note that there is a "
                            + "quickfix in the IDE which lets you perform conversion. Previously, launcher "
                            + "icons were required to be in the PNG format but that restriction is no longer "
                            + "there, so lint now flags these.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            IconDetector.class,
                            EnumSet.of(Scope.JAVA_FILE_SCOPE, Scope.RESOURCE_FILE_SCOPE)));

    private int mMinSdk;

    public IconDetector() {}

    @Override
    public void beforeCheckRootProject(Context context) {
        mMinSdk = context.getMainProject().getMinSdkVersion();
    }

    @Override
    public void afterCheckEachProject(Context context) {
    }

    @Override
    public boolean filterIncident(Context context, Incident incident) {
        if (incident.getIssue() == ISSUE && mMinSdk < 18) {
            return false;
        }
        return true;
    }

    @Override
    public boolean appliesTo(Context context, File file) {
        String path = file.getPath();
        return path.contains("res" + File.separator + "drawable")
                || path.contains("res" + File.separator + "mipmap")
                || file.getName().endsWith(".xml");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("ImageView", "bitmap", "item");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            String name = attr.getName();
            String value = attr.getValue();
            if (value == null) {
                continue;
            }
            boolean isDrawableAttribute =
                    name.endsWith(":src")
                            || name.equals("src")
                            || name.endsWith(":background")
                            || name.equals("background")
                            || name.endsWith(":drawable")
                            || name.equals("drawable")
                            || name.endsWith(":icon")
                            || name.equals("icon");
            if ((isDrawableAttribute
                            && (value.startsWith("@drawable/")
                                    || value.startsWith("@mipmap/")))
                    || isPngOrJpegReference(value)) {
                context.report(
                        ISSUE,
                        attr,
                        context.getLocation(attr),
                        "Consider converting this image to WebP to reduce APK size");
            }
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UCallExpression.class,
                USimpleNameReferenceExpression.class,
                UMethod.class,
                UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(UMethod node) {
            }

            @Override
            public void visitClass(UClass node) {
            }

            @Override
            public void visitCallExpression(UCallExpression node) {
                PsiMethod method = node.resolve();
                if (method != null) {
                    String name = method.getName();
                    PsiClass containingClass = method.getContainingClass();
                    String className =
                            containingClass != null ? containingClass.getQualifiedName() : null;
                    if ("android.graphics.BitmapFactory".equals(className)
                            && name != null
                            && name.startsWith("decode")) {
                        context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "Consider decoding a WebP image instead of PNG/JPEG");
                        return;
                    }
                }

                for (UExpression arg : node.getValueArguments()) {
                    String source = arg.asSourceString();
                    if (source.contains("R.drawable.")
                            || source.contains("R.mipmap.")
                            || isPngOrJpegReference(source)) {
                        String methodName = method != null ? method.getName() : "";
                        if ("setImageResource".equals(methodName)
                                || "setImageDrawable".equals(methodName)
                                || "setBackgroundResource".equals(methodName)
                                || "setImageBitmap".equals(methodName)) {
                            context.report(
                                    ISSUE,
                                    node,
                                    context.getLocation(node),
                                    "Consider using a WebP drawable to reduce APK size");
                            return;
                        }
                    }
                }
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    USimpleNameReferenceExpression node) {
                String identifier = node.getIdentifier();
                if (identifier != null && isPngOrJpegReference(identifier)) {
                    context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Consider converting this image to WebP to reduce APK size");
                }
            }
        };
    }

    private static boolean isPngOrJpegReference(String value) {
        String lower = value.toLowerCase();
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg");
    }
}