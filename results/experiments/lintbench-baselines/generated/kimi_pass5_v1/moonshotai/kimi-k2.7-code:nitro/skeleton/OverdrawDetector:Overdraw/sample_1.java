package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UIdentifier;
import org.jetbrains.uast.USimpleNameReferenceExpression;

public class OverdrawDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(OverdrawDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "Overdraw",
                    "Overdraw: Painting regions more than once",
                    "If you set a background drawable on a root view, then you should use "
                            + "a custom theme where the theme background is null. Otherwise, the "
                            + "theme background will be painted first, only to have your custom "
                            + "background completely cover it; this is called \"overdraw\".\n\n"
                            + "If you want your custom background on multiple pages, then you "
                            + "should consider making a custom theme with your custom background "
                            + "and just using that theme instead of a root element background.\n\n"
                            + "Of course it's possible that your custom drawable is translucent and "
                            + "you want it to be mixed with the background. However, you will get "
                            + "better performance if you pre-mix the background with your drawable "
                            + "and use that resulting image or color as a custom theme background "
                            + "instead.",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final String CLASS_ACTIVITY = "android.app.Activity";
    private static final String ATTR_BACKGROUND = "background";
    private static final String SET_CONTENT_VIEW = "setContentView";
    private static final String MESSAGE =
            "Possible overdraw: the root view has a background drawable, and this layout is used "
                    + "as the content view of an Activity. The activity theme background may be "
                    + "painted first and then completely covered. Consider using a custom theme "
                    + "with android:windowBackground=\"@null\".";

    private final Map<String, Boolean> mIsActivity = new HashMap<>();
    private final Map<String, String> mActivityToLayout = new HashMap<>();
    private final Map<String, Location> mRootBackgroundLocations = new HashMap<>();

    private String mCurrentLayoutName;

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return folderType == com.android.resources.ResourceFolderType.LAYOUT;
    }

    @Override
    public void beforeCheckProject(Context context) {
        mIsActivity.clear();
        mActivityToLayout.clear();
        mRootBackgroundLocations.clear();
    }

    @Override
    public void beforeCheckFile(Context context) {
        mCurrentLayoutName = null;
        if (context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            String fileName = xmlContext.file.getName();
            if (fileName != null && fileName.endsWith(".xml")) {
                mCurrentLayoutName = fileName.substring(0, fileName.length() - 4);
            }
        }
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_BACKGROUND);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void visitAttribute(XmlContext context, org.w3c.dom.Attr attribute) {
        String localName = attribute.getLocalName();
        if (localName == null) {
            localName = attribute.getName();
        }
        if (!ATTR_BACKGROUND.equals(localName)) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || "@null".equals(value)) {
            return;
        }

        org.w3c.dom.Element owner = attribute.getOwnerElement();
        org.w3c.dom.Node parent = owner.getParentNode();
        if (parent != null
                && parent.getNodeType() == org.w3c.dom.Node.DOCUMENT_NODE
                && mCurrentLayoutName != null) {
            mRootBackgroundLocations.put(mCurrentLayoutName, context.getLocation(attribute));
        }
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        // Root background detection is handled in visitAttribute.
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(CLASS_ACTIVITY);
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName != null) {
            boolean isActivity = context.getEvaluator().extendsClass(declaration, CLASS_ACTIVITY, false);
            mIsActivity.put(qualifiedName, isActivity);
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        List<Class<? extends UElement>> types = new ArrayList<>(1);
        types.add(UCallExpression.class);
        return types;
    }

    public void visitSimpleNameReferenceExpression(JavaContext context, USimpleNameReferenceExpression expression) {
        // Not used directly; layout resolution is performed when visiting setContentView calls.
    }

    public void visitCallExpression(JavaContext context, UCallExpression node) {
        UIdentifier identifier = node.getMethodIdentifier();
        if (identifier == null) {
            return;
        }

        if (!SET_CONTENT_VIEW.equals(identifier.getName())) {
            return;
        }

        UClass activity = findEnclosingActivity(context, node);
        if (activity == null) {
            return;
        }

        List<UExpression> arguments = node.getValueArguments();
        if (arguments.isEmpty()) {
            return;
        }

        String layoutName = getLayoutName(arguments.get(0));
        if (layoutName != null && !layoutName.isEmpty()) {
            String qualifiedName = activity.getQualifiedName();
            if (qualifiedName != null) {
                mActivityToLayout.put(qualifiedName, layoutName);
            }
        }
    }

    @Override
    public void afterCheckRootProject(Context context) {
        Set<String> layouts = new HashSet<>(mActivityToLayout.values());
        for (String layoutName : layouts) {
            Location location = mRootBackgroundLocations.get(layoutName);
            if (location != null) {
                context.report(ISSUE, location, MESSAGE);
            }
        }
    }

    private UClass findEnclosingActivity(JavaContext context, UElement node) {
        UClass current = getContainingUClass(node);
        while (current != null) {
            if (isActivity(context, current)) {
                return current;
            }
            current = getParentUClass(current);
        }
        return null;
    }

    private boolean isActivity(JavaContext context, UClass declaration) {
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName == null) {
            return false;
        }
        Boolean cached = mIsActivity.get(qualifiedName);
        if (cached != null) {
            return cached;
        }
        boolean result = context.getEvaluator().extendsClass(declaration, CLASS_ACTIVITY, false);
        mIsActivity.put(qualifiedName, result);
        return result;
    }

    private static UClass getContainingUClass(UElement node) {
        UElement current = node;
        while (current != null) {
            if (current instanceof UClass) {
                return (UClass) current;
            }
            current = current.getUastParent();
        }
        return null;
    }

    private static UClass getParentUClass(UClass cls) {
        UElement parent = cls.getUastParent();
        while (parent != null) {
            if (parent instanceof UClass) {
                return (UClass) parent;
            }
            parent = parent.getUastParent();
        }
        return null;
    }

    private static String getLayoutName(UExpression expression) {
        if (expression == null) {
            return null;
        }
        String source = expression.asSourceString();
        if (source == null) {
            return null;
        }

        int index = source.lastIndexOf("R.layout.");
        if (index == -1) {
            return null;
        }

        String name = source.substring(index + "R.layout.".length());
        int end = name.length();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!Character.isJavaIdentifierPart(c)) {
                end = i;
                break;
            }
        }
        return name.substring(0, end);
    }
}