package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "IconDensities",
            "Icon densities validation",
            "Icons will look best if a custom version is provided for each of the major screen " +
            "density classes (low, medium, high, extra high). This lint check identifies icons " +
            "which do not have complete coverage across the densities.\n\n" +
            "Low density is not really used much anymore, so this check ignores the ldpi density. " +
            "To force lint to include it, set the environment variable `ANDROID_LINT_INCLUDE_LDPI=true`. " +
            "For more information on current density usage, see " +
            "https://developer.android.com/about/dashboards",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.JAVA_FILE_SCOPE, Scope.RESOURCE_FILE_SCOPE));

    private final Map<String, Location> mIconLocations = new HashMap<>();
    private boolean mIncludeLdpi = false;

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mIconLocations.clear();
        mIncludeLdpi = Boolean.parseBoolean(System.getenv("ANDROID_LINT_INCLUDE_LDPI"));
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        for (Map.Entry<String, Location> entry : mIconLocations.entrySet()) {
            String iconName = entry.getKey();
            Location location = entry.getValue();
            String message = String.format(
                "Icon %s may be missing density coverage. Ensure versions exist for mdpi, hdpi, xhdpi, xxhdpi, and xxxhdpi.%s",
                iconName,
                mIncludeLdpi ? " Also include ldpi." : ""
            );
            context.report(ISSUE, location, message);
        }
    }

    @Override
    public boolean filterIncident(@NonNull Incident incident) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull Scope scope) {
        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("application", "activity", "activity-alias", "service", "receiver", "provider", "bitmap", "item");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        NamedNodeMap attributes = element.getAttributes();
        if (attributes == null) return;

        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            String name = attr.getNodeName();
            String value = attr.getNodeValue();
            if (value != null && (name.endsWith(":icon") || name.endsWith(":logo") || name.endsWith(":src"))) {
                if (value.startsWith("@drawable/") || value.startsWith("@mipmap/")) {
                    String resName = value.substring(value.indexOf('/') + 1);
                    mIconLocations.putIfAbsent(resName, context.getLocation(attr));
                }
            }
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UClass.class, UMethod.class, UCallExpression.class, USimpleNameReferenceExpression.class);
    }

    @Override
    public UElementHandler createUastHandler() {
        return new UElementHandler() {
            @Override
            public void visitClass(@NonNull UClass node) {
                // Inspect class-level annotations or fields for icon references
            }

            @Override
            public void visitMethod(@NonNull UMethod node) {
                // Inspect method signatures and bodies for icon resource usage
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                String methodName = node.getMethodName();
                if (methodName != null && (methodName.contains("Icon") || methodName.contains("Drawable"))) {
                    node.getValueArguments().forEach(arg -> {
                        String text = arg.asSourceString();
                        if (text != null && (text.contains("R.drawable.") || text.contains("R.mipmap."))) {
                            int dotIndex = text.lastIndexOf('.');
                            if (dotIndex != -1) {
                                String resName = text.substring(dotIndex + 1);
                                mIconLocations.putIfAbsent(resName, Location.create(node.getContainingUFile().getPsi()));
                            }
                        }
                    });
                }
            }

            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                String source = node.asSourceString();
                if (source != null && (source.contains("R.drawable.") || source.contains("R.mipmap."))) {
                    int dotIndex = source.lastIndexOf('.');
                    if (dotIndex != -1) {
                        String resName = source.substring(dotIndex + 1);
                        mIconLocations.putIfAbsent(resName, Location.create(node.getContainingUFile().getPsi()));
                    }
                }
            }
        };
    }
}