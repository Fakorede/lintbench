package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiElement;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class AlwaysShowActionDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AlwaysShowActionDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "AlwaysShowAction",
                    "Usage of `showAsAction=always`",
                    "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS` in " +
                    "Java code is usually a deviation from the user interface style guide. Use `ifRoom` or " +
                    "the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.\n\n" +
                    "If `always` is used sparingly there are usually no problems and behavior is " +
                    "roughly equivalent to `ifRoom` but with preference over other `ifRoom` " +
                    "items. Using it more than twice in the same menu is a bad idea.\n\n" +
                    "This check looks for menu XML files that contain more than two `always` " +
                    "actions, or some `always` actions and no `ifRoom` actions. In Java code, " +
                    "it looks for projects that contain references to `MenuItem.SHOW_AS_ACTION_ALWAYS` " +
                    "and no references to `MenuItem.SHOW_AS_ACTION_IF_ROOM`.",
                    Category.USABILITY,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private int mXmlAlwaysCount = 0;
    private int mXmlIfRoomCount = 0;

    private boolean mJavaHasAlways = false;
    private boolean mJavaHasIfRoom = false;
    private UReferenceExpression mJavaAlwaysNode = null;
    private JavaContext mJavaAlwaysContext = null;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MENU;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("showAsAction");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mXmlAlwaysCount = 0;
        mXmlIfRoomCount = 0;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value != null) {
            if (value.contains("always")) {
                mXmlAlwaysCount++;
            }
            if (value.contains("ifRoom")) {
                mXmlIfRoomCount++;
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (context instanceof XmlContext) {
            if (mXmlAlwaysCount > 2 || (mXmlAlwaysCount > 0 && mXmlIfRoomCount == 0)) {
                XmlContext xmlContext = (XmlContext) context;
                Element root = xmlContext.getDocument().getDocumentElement();
                if (root != null) {
                    xmlContext.report(ISSUE, root, xmlContext.getLocation(root),
                        "Consider using `ifRoom` instead of `always`");
                }
            }
        }
    }

    @Override
    public List<String> getApplicableReferenceNames() {
        return Arrays.asList("SHOW_AS_ACTION_ALWAYS", "SHOW_AS_ACTION_IF_ROOM");
    }

    @Override
    public void visitReference(
            @NonNull JavaContext context,
            @NonNull UReferenceExpression reference,
            @NonNull PsiElement referenced) {
        String name = reference.getIdentifier();
        if ("SHOW_AS_ACTION_ALWAYS".equals(name)) {
            mJavaHasAlways = true;
            if (mJavaAlwaysNode == null) {
                mJavaAlwaysNode = reference;
                mJavaAlwaysContext = context;
            }
        } else if ("SHOW_AS_ACTION_IF_ROOM".equals(name)) {
            mJavaHasIfRoom = true;
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mJavaHasAlways && !mJavaHasIfRoom && mJavaAlwaysContext != null && mJavaAlwaysNode != null) {
            mJavaAlwaysContext.report(ISSUE, mJavaAlwaysNode, mJavaAlwaysContext.getLocation(mJavaAlwaysNode),
                "Consider using `SHOW_AS_ACTION_IF_ROOM` instead of `SHOW_AS_ACTION_ALWAYS`");
        }
    }
}