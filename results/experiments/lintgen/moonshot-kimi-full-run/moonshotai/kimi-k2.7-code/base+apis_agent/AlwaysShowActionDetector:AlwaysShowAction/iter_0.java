package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceFolderType;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class AlwaysShowActionDetector extends Detector implements XmlScanner, SourceCodeScanner {

    private static final String SHOW_AS_ACTION = "showAsAction";
    private static final String ALWAYS = "always";
    private static final String IF_ROOM = "ifRoom";
    private static final String ITEM = "item";

    private final List<Location> mAlwaysReferences = new ArrayList<>();
    private final List<Location> mIfRoomReferences = new ArrayList<>();

    public static final Issue ISSUE = Issue.create(
            "AlwaysShowAction",
            "Usage of showAsAction=always",
            "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS` in Java code is usually a deviation from the user interface style guide. Use `ifRoom` or the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.\n\nIf `always` is used sparingly there are usually no problems and behavior is roughly equivalent to `ifRoom` but with preference over other `ifRoom` items. Using it more than twice in the same menu is a bad idea.\n\nThis check looks for menu XML files that contain more than two `always` actions, or some `always` actions and no `ifRoom` actions. In Java code, it looks for projects that contain references to `MenuItem.SHOW_AS_ACTION_ALWAYS` and no references to `MenuItem.SHOW_AS_ACTION_IF_ROOM`.",
            Category.USABILITY,
            3,
            Severity.WARNING,
            new Implementation(
                    AlwaysShowActionDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE_SCOPE, Scope.JAVA_FILE_SCOPE)
            )
    );

    @Override
    @NotNull
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("menu");
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.MENU) {
            return;
        }

        int alwaysCount = 0;
        int ifRoomCount = 0;

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && ITEM.equals(child.getNodeName())) {
                NamedNodeMap attributes = child.getAttributes();
                for (int j = 0; j < attributes.getLength(); j++) {
                    Node attr = attributes.item(j);
                    String localName = attr.getLocalName();
                    String name = attr.getName();
                    if (SHOW_AS_ACTION.equals(localName)
                            || (localName == null && SHOW_AS_ACTION.equals(name))) {
                        String value = attr.getNodeValue();
                        if (value != null) {
                            for (String flag : value.split("\\|")) {
                                String trimmed = flag.trim();
                                if (ALWAYS.equals(trimmed)) {
                                    alwaysCount++;
                                } else if (IF_ROOM.equals(trimmed)) {
                                    ifRoomCount++;
                                }
                            }
                        }
                        break;
                    }
                }
            }
        }

        if (alwaysCount > 2) {
            context.report(
                    ISSUE,
                    context.getLocation(element),
                    "Menu has more than two showAsAction=\"always\" items (found " + alwaysCount + ")"
            );
        } else if (alwaysCount > 0 && ifRoomCount == 0) {
            context.report(
                    ISSUE,
                    context.getLocation(element),
                    "Menu has showAsAction=\"always\" items but no showAsAction=\"ifRoom\" items"
            );
        }
    }

    @Override
    @NotNull
    public List<String> getApplicableReferenceNames() {
        return Arrays.asList("SHOW_AS_ACTION_ALWAYS", "SHOW_AS_ACTION_IF_ROOM");
    }

    @Override
    public void visitReference(@NotNull JavaContext context, @NotNull UReferenceExpression reference,
            @NotNull PsiElement referenced) {
        String name = reference.getReferenceName();
        if ("SHOW_AS_ACTION_ALWAYS".equals(name)) {
            mAlwaysReferences.add(context.getLocation(reference));
        } else if ("SHOW_AS_ACTION_IF_ROOM".equals(name)) {
            mIfRoomReferences.add(context.getLocation(reference));
        }
    }

    @Override
    public void beforeCheckEachProject(@NotNull Context context) {
        mAlwaysReferences.clear();
        mIfRoomReferences.clear();
    }

    @Override
    public void afterCheckEachProject(@NotNull Context context) {
        if (!mAlwaysReferences.isEmpty() && mIfRoomReferences.isEmpty()) {
            Location location = mAlwaysReferences.get(0);
            context.report(
                    ISSUE,
                    location,
                    "This project references MenuItem.SHOW_AS_ACTION_ALWAYS but not MenuItem.SHOW_AS_ACTION_IF_ROOM"
            );
        }
    }
}