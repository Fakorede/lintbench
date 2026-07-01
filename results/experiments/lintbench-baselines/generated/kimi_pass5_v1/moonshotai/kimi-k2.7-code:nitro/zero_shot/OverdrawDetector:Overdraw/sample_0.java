package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_BACKGROUND;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PACKAGE;
import static com.android.SdkConstants.ATTR_THEME;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_APPLICATION;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.utils.XmlUtils;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UastUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class OverdrawDetector extends ResourceXmlDetector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
        "Overdraw",
        "Overdraw: Painting regions more than once",
        "If you set a background drawable on a root view, then you should use a custom theme "
            + "where the theme background is null. Otherwise, the theme background will be painted "
            + "first, only to have your custom background completely cover it.\n\n"
            + "If you want your custom background on multiple pages, then you should consider "
            + "making a custom theme with your custom background and just using that theme instead "
            + "of a root element background.\n\n"
            + "Of course it's possible that your custom drawable is translucent and you want it to "
            + "be mixed with the background. However, you will get better performance if you "
            + "pre-mix the background with your drawable and use that resulting image or color as "
            + "a custom theme background instead.",
        Category.PERFORMANCE,
        5,
        Severity.WARNING,
        new Implementation(OverdrawDetector.class,
            Scope.JAVA_FILE_SCOPE,
            Scope.RESOURCE_FILE_SCOPE)
    );

    private static final String MESSAGE =
        "Possible overdraw: this root view has a custom background, which may cover the "
            + "activity theme's window background. Consider using a theme with `@null` "
            + "windowBackground or pre-mixing the background with this drawable.";

    private static final Pattern SET_CONTENT_VIEW_PATTERN =
        Pattern.compile("\\bR\\.layout\\.([A-Za-z_]\\w*)\\b");

    private final Map<String, Set<String>> mLayoutToActivities = new HashMap<>();
    private final Map<String, LayoutCandidate> mLayoutCandidates = new HashMap<>();
    private final Map<String, StyleInfo> mProjectStyles = new HashMap<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("setContentView");
    }

    @Override
    public void visitMethodCall(
        @NonNull JavaContext context,
        @NonNull UCallExpression node,
        @NonNull PsiMethod method) {
        if (!"setContentView".equals(method.getName())) {
            return;
        }

        List<UExpression> args = node.getValueArguments();
        if (args.isEmpty()) {
            return;
        }

        UExpression arg = args.get(0);
        PsiElement sourcePsi = arg.getSourcePsi();
        if (sourcePsi == null) {
            return;
        }

        String text = sourcePsi.getText();
        Matcher matcher = SET_CONTENT_VIEW_PATTERN.matcher(text);
        if (!matcher.find()) {
            return;
        }

        String layout = matcher.group(1);
        UClass cls = UastUtils.getParentOfType(node, UClass.class, false);
        if (cls == null) {
            return;
        }

        String name = cls.getQualifiedName();
        if (name == null) {
            name = cls.getName();
        }
        if (name == null) {
            return;
        }

        Set<String> activities = mLayoutToActivities.get(layout);
        if (activities == null) {
            activities = new HashSet<>();
            mLayoutToActivities.put(layout, activities);
        }
        activities.add(name);
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        Attr background = root.getAttributeNodeNS(ANDROID_URI, ATTR_BACKGROUND);
        if (background == null) {
            return;
        }

        String layoutName = getBaseName(context.file.getName());
        mLayoutCandidates.put(layoutName,
            new LayoutCandidate(context, context.getValueLocation(background)));
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mLayoutToActivities.clear();
        mLayoutCandidates.clear();
        mProjectStyles.clear();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mProjectStyles.isEmpty()) {
            loadProjectStyles(context.getMainProject());
        }

        for (String layoutName : mLayoutToActivities.keySet()) {
            LayoutCandidate candidate = mLayoutCandidates.get(layoutName);
            if (candidate == null) {
                continue;
            }

            if (hasThemedBackground(context, layoutName)) {
                candidate.context.report(ISSUE, candidate.location, MESSAGE);
            }
        }
    }

    private boolean hasThemedBackground(@NonNull Context context, @NonNull String layoutName) {
        Project project = context.getMainProject();
        File manifest = project.getManifestFile();
        if (manifest == null || !manifest.exists()) {
            return true;
        }

        Document manifestDoc = XmlUtils.parseDocumentSilently(manifest, true);
        if (manifestDoc == null) {
            return true;
        }

        String packageName = manifestDoc.getDocumentElement().getAttribute(ATTR_PACKAGE);
        Element application = (Element) manifestDoc.getDocumentElement()
            .getElementsByTagName(TAG_APPLICATION).item(0);
        String appTheme = application != null
            ? application.getAttributeNS(ANDROID_URI, ATTR_THEME) : null;

        Collection<String> activities = mLayoutToActivities.get(layoutName);
        if (activities == null || activities.isEmpty()) {
            return false;
        }

        NodeList activityNodes = manifestDoc.getDocumentElement()
            .getElementsByTagName(TAG_ACTIVITY);
        for (String activityClass : activities) {
            boolean found = false;
            String activityTheme = null;

            for (int i = 0, n = activityNodes.getLength(); i < n; i++) {
                Element activity = (Element) activityNodes.item(i);
                String name = activity.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if (name.startsWith(".")) {
                    name = packageName + name;
                }

                if (activityClass.equals(name)) {
                    found = true;
                    activityTheme = activity.getAttributeNS(ANDROID_URI, ATTR_THEME);
                    break;
                }
            }

            if (!found) {
                continue;
            }

            String theme = (activityTheme != null && !activityTheme.isEmpty())
                ? activityTheme
                : appTheme;
            if (theme == null || theme.isEmpty()) {
                return true;
            }

            if (isNullWindowBackground(theme)) {
                continue;
            }

            return true;
        }

        return false;
    }

    private void loadProjectStyles(@NonNull Project project) {
        mProjectStyles.clear();

        for (File resDir : project.getResourceDirectories()) {
            File[] valuesDirs = resDir.listFiles(
                (dir, name) -> name != null && name.startsWith("values"));
            if (valuesDirs == null) {
                continue;
            }

            for (File valuesDir : valuesDirs) {
                File[] files = valuesDir.listFiles(
                    (dir, name) -> name != null && name.endsWith(".xml"));
                if (files == null) {
                    continue;
                }

                for (File file : files) {
                    Document doc = XmlUtils.parseDocumentSilently(file, true);
                    if (doc == null) {
                        continue;
                    }

                    NodeList styles = doc.getElementsByTagName("style");
                    for (int i = 0, n = styles.getLength(); i < n; i++) {
                        Element style = (Element) styles.item(i);
                        String name = style.getAttribute("name");
                        if (name.isEmpty()) {
                            continue;
                        }

                        String parent = extractStyleName(style.getAttribute("parent"));
                        if (parent.isEmpty()) {
                            int dot = name.lastIndexOf('.');
                            if (dot > 0) {
                                parent = name.substring(0, dot);
                            }
                        }

                        Map<String, String> items = new HashMap<>();
                        NodeList itemNodes = style.getElementsByTagName("item");
                        for (int j = 0, m = itemNodes.getLength(); j < m; j++) {
                            Element item = (Element) itemNodes.item(j);
                            String itemName = item.getAttribute("name");
                            if (!itemName.isEmpty()) {
                                items.put(itemName, item.getTextContent().trim());
                            }
                        }

                        mProjectStyles.put(name, new StyleInfo(name, parent, items));
                    }
                }
            }
        }
    }

    private boolean isNullWindowBackground(@NonNull String themeRef) {
        if (themeRef.startsWith("@android:style/")) {
            return false;
        }
        String localName = extractStyleName(themeRef);
        if (localName.isEmpty()) {
            return false;
        }
        StyleInfo style = mProjectStyles.get(localName);
        if (style == null) {
            return false;
        }
        return hasNullWindowBackground(style, new HashSet<>());
    }

    private boolean hasNullWindowBackground(
        @NonNull StyleInfo style,
        @NonNull Set<String> seen) {
        if (!seen.add(style.name)) {
            return false;
        }

        String value = style.items.get("android:windowBackground");
        if (value != null) {
            return "@null".equals(value);
        }

        if (style.parent != null && !style.parent.isEmpty()) {
            StyleInfo parent = mProjectStyles.get(style.parent);
            if (parent != null) {
                return hasNullWindowBackground(parent, seen);
            }
        }

        return false;
    }

    @NonNull
    private static String extractStyleName(@NonNull String ref) {
        int slash = ref.lastIndexOf('/');
        return slash >= 0 ? ref.substring(slash + 1) : ref;
    }

    @NonNull
    private static String getBaseName(@NonNull String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(0, dot) : fileName;
    }

    private static final class LayoutCandidate {
        final XmlContext context;
        final Location location;

        LayoutCandidate(@NonNull XmlContext context, @NonNull Location location) {
            this.context = context;
            this.location = location;
        }
    }

    private static final class StyleInfo {
        final String name;
        final String parent;
        final Map<String, String> items;

        StyleInfo(
            @NonNull String name,
            @Nullable String parent,
            @NonNull Map<String, String> items) {
            this.name = name;
            this.parent = parent;
            this.items = items;
        }
    }
}