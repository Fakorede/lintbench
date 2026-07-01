package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.io.File;
import java.io.FileInputStream;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner, BinaryResourceScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(IconDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "ConvertToWebp",
                    "Convert to WebP",
                    "The WebP format is typically more compact than PNG and JPEG. As of Android 4.2.1  " +
                    "it supports transparency and lossless conversion as well. Note that there is a  " +
                    "quickfix in the IDE which lets you perform conversion. " +
                    "Previously, launcher icons were required to be in the PNG format but that  " +
                    "restriction is no longer there, so lint now flags these.",
                    Category.ICONS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return null;
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {
            }
            
            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
            }
            
            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
            }
        };
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        File file = context.file;
        String name = file.getName();
        if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            if (name.endsWith(".9.png")) {
                return;
            }
            int minSdk = getMinSdk(context);
            if (minSdk < 14) {
                return;
            }
            boolean isLeftToConvert = false;
            if (minSdk >= 18) {
                isLeftToConvert = true;
            } else {
                if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
                    isLeftToConvert = true;
                } else if (name.endsWith(".png")) {
                    isLeftToConvert = !hasAlpha(file);
                }
            }
            if (isLeftToConvert) {
                String message = "The image format can be converted to WebP";
                Incident incident = new Incident(ISSUE, context.getLocation(file), message);
                context.report(incident);
            }
        }
    }

    private static int getMinSdk(@NonNull Context context) {
        try {
            return context.getProject().getMinSdk();
        } catch (Throwable t) {
            try {
                return context.getProject().getMinSdkVersion().getFeatureLevel();
            } catch (Throwable t2) {
                return 1;
            }
        }
    }

    private static boolean hasAlpha(@NonNull File file) {
        String name = file.getName();
        if (name.endsWith(".png")) {
            return pngHasAlpha(file);
        }
        return false;
    }

    private static boolean pngHasAlpha(@NonNull File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[30];
            if (fis.read(header) < 30) {
                return true;
            }
            if (header[0] == (byte) 0x89 && header[1] == (byte) 0x50 && header[2] == (byte) 0x4E && header[3] == (byte) 0x47) {
                int colorType = header[25] & 0xFF;
                if (colorType == 4 || colorType == 6) {
                    return true;
                }
                if (colorType == 3) {
                    return true; 
                }
                return false;
            }
        } catch (Throwable t) {
            // ignore
        }
        return true;
    }
}