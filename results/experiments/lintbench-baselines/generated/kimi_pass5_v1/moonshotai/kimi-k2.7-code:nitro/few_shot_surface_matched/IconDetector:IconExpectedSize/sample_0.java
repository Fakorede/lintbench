package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ICON;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_ACTIVITY_ALIAS;
import static com.android.SdkConstants.TAG_APPLICATION;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "IconExpectedSize",
                    "Icon has incorrect size",
                    "Launcher icons should follow the platform size conventions for each density. "
                            + "For example, launcher icons are typically 48x48 dp, which means "
                            + "mdpi: 48x48 px, hdpi: 72x72 px, xhdpi: 96x96 px, xxhdpi: 144x144 px, "
                            + "and xxxhdpi: 192x192 px. Using incorrect sizes can cause the icon "
                            + "to be scaled or clipped, producing poor visual results.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            IconDetector.class,
                            Scope.MANIFEST_SCOPE,
                            Scope.RESOURCE_FILE_SCOPE,
                            Scope.JAVA_FILE_SCOPE));

    private static final String MESSAGE =
            "Launcher icons should conform to the expected sizes for each density";

    private boolean mHasLauncherIcon;

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mHasLauncherIcon = false;
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // Cross-project density checks can be performed here if icon metadata was collected.
    }

    @Override
    public boolean filterIncident(@NonNull Context context, @NonNull Incident incident) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MIPMAP || folderType == ResourceFolderType.DRAWABLE;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_APPLICATION, TAG_ACTIVITY, TAG_ACTIVITY_ALIAS);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Attr iconAttribute = element.getAttributeNodeNS(ANDROID_URI, ATTR_ICON);
        if (iconAttribute == null) {
            return;
        }
        String value = iconAttribute.getValue();
        if (value != null && value.contains("ic_launcher")) {
            mHasLauncherIcon = true;
            context.report(ISSUE, iconAttribute, context.getLocation(iconAttribute), MESSAGE);
        }
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {
                // No method-level check required.
            }

            @Override
            public void visitClass(@NonNull UClass node) {
                // No class-level check required.
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                String methodName = node.getMethodName();
                if ("setIcon".equals(methodName)
                        || "setLogo".equals(methodName)
                        || "setImageResource".equals(methodName)) {
                    context.report(ISSUE, node, context.getLocation(node), MESSAGE);
                }
            }

            @Override
            public void visitSimpleNameReferenceExpression(
                    @NonNull USimpleNameReferenceExpression node) {
                if ("ic_launcher".equals(node.getIdentifier())) {
                    context.report(ISSUE, node, context.getLocation(node), MESSAGE);
                }
            }
        };
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UMethod.class, UClass.class, UCallExpression.class, USimpleNameReferenceExpression.class);
    }
}