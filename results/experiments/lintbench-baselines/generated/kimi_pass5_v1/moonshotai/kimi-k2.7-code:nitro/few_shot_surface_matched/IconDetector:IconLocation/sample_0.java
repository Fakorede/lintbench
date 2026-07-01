package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceValue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.XmlScannerConstants;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UQualifiedReferenceExpression;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements XmlScanner, SourceCodeScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String DRAWABLE_PREFIX = "@drawable/";

    private static final Implementation IMPLEMENTATION =
            new Implementation(IconDetector.class, Scope.RESOURCE_FILE_SCOPE, Scope.JAVA_FILE_SCOPE);

    public static final Issue ICON_LOCATION =
            Issue.create(
                    "IconLocation",
                    "Image defined in density-independent drawable folder",
                    "The res/drawable folder is intended for density-independent graphics such as "
                            + "shapes defined in XML. For bitmaps, move it to `drawable-mdpi` and "
                            + "consider providing higher and lower resolution versions in "
                            + "`drawable-ldpi`, `drawable-hdpi` and `drawable-xhdpi`. If the icon "
                            + "really is density independent (for example a solid color) you can "
                            + "place it in `drawable-nodpi`.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private Set<String> mReferences;
    private Map<String, File> mDrawableFiles;
    private boolean mDrawableFilesInitialized;

    @Override
    public void beforeCheckRootProject(Context context) {
        mReferences = new HashSet<>();
        mDrawableFiles = new HashMap<>();
        mDrawableFilesInitialized = false;
    }

    @Override
    public boolean appliesTo(Context context, File file) {
        String path = file.getPath();
        return path.endsWith(".java") || (path.contains("/res/") && path.endsWith(".xml"));
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String[] drawableAttributes = {"src", "background", "icon", "logo", "drawable"};
        for (String attribute : drawableAttributes) {
            if (!element.hasAttributeNS(ANDROID_URI, attribute)) {
                continue;
            }
            String value = element.getAttributeNS(ANDROID_URI, attribute);
            if (value.startsWith(DRAWABLE_PREFIX)) {
                String name = value.substring(DRAWABLE_PREFIX.length());
                if (!name.isEmpty()) {
                    mReferences.add(name);
                    context.report(
                            ICON_LOCATION,
                            element,
                            context.getLocation(element),
                            getMessage(name),
                            name);
                }
            }
        }
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new IconUastHandler(context);
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UClass.class, UMethod.class, UCallExpression.class, USimpleNameReferenceExpression.class);
    }

    @Override
    public void afterCheckEachProject(Context context) {
        for (Map.Entry<String, File> entry : getDrawableFiles(context).entrySet()) {
            String name = entry.getKey();
            File file = entry.getValue();
            if (!mReferences.contains(name)) {
                context.report(ICON_LOCATION, Location.create(file), getMessage(name));
            }
        }
    }

    @Override
    public boolean filterIncident(
            Context context,
            Issue issue,
            Severity severity,
            Location location,
            String message,
            Object data) {
        if (issue != ICON_LOCATION || !(data instanceof String)) {
            return true;
        }
        String name = (String) data;
        return getDrawableFiles(context).containsKey(name);
    }

    private Map<String, File> getDrawableFiles(Context context) {
        if (!mDrawableFilesInitialized) {
            mDrawableFilesInitialized = true;
            for (File res : context.getProject().getResourceFolders()) {
                File drawable = new File(res, "drawable");
                if (!drawable.isDirectory()) {
                    continue;
                }
                File[] files = drawable.listFiles();
                if (files == null) {
                    continue;
                }
                for (File file : files) {
                    String base = getBaseName(file.getName());
                    if (base != null) {
                        mDrawableFiles.put(base, file);
                    }
                }
            }
        }
        return mDrawableFiles;
    }

    private String getBaseName(String fileName) {
        if (fileName.endsWith(".9.png")) {
            return fileName.substring(0, fileName.length() - ".9.png".length());
        }
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) {
            return null;
        }
        String ext = fileName.substring(dot + 1);
        if (!ext.equals("png")
                && !ext.equals("jpg")
                && !ext.equals("jpeg")
                && !ext.equals("gif")
                && !ext.equals("webp")) {
            return null;
        }
        return fileName.substring(0, dot);
    }

    private String getMessage(String name) {
        return "The image `"
                + name
                + "` is defined in the density-independent `res/drawable` folder";
    }

    private class IconUastHandler extends UElementHandler {
        private final JavaContext mContext;

        IconUastHandler(JavaContext context) {
            mContext = context;
        }

        @Override
        public void visitClass(UClass node) {
        }

        @Override
        public void visitMethod(UMethod node) {
        }

        @Override
        public void visitCallExpression(UCallExpression node) {
            for (UExpression arg : node.getValueArguments()) {
                ResourceValue resource = mContext.getEvaluator().getResourceFieldValue(arg);
                if (resource != null && "drawable".equals(resource.getResourceType())) {
                    String name = resource.getName();
                    if (name != null && !name.isEmpty()) {
                        mReferences.add(name);
                    }
                }
            }
        }

        @Override
        public void visitSimpleNameReferenceExpression(USimpleNameReferenceExpression node) {
            UElement parent = node.getUastParent();
            if (!(parent instanceof UQualifiedReferenceExpression)) {
                return;
            }
            UQualifiedReferenceExpression qualified = (UQualifiedReferenceExpression) parent;
            if (qualified.getSelector() != node) {
                return;
            }
            String receiver = qualified.getReceiver().asSourceString();
            if (!"R.drawable".equals(receiver)) {
                return;
            }
            String name = node.getIdentifier();
            if (name.isEmpty()) {
                return;
            }
            mReferences.add(name);
            mContext.report(
                    ICON_LOCATION,
                    node,
                    mContext.getLocation(node),
                    getMessage(name),
                    name);
        }
    }
}