com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AlwaysShowActionDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "AlwaysShowAction",
            "Usage of `showAsAction=always`",
            "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS` in " +
            "Java code is usually a deviation from the user interface style guide. Use `ifRoom` " +
            "or the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.\n\n" +
            "If `always` is used sparingly there are usually no problems and behavior is " +
            "roughly equivalent to `ifRoom` but with preference over other `ifRoom` " +
            "items. Using it more than twice in the same menu is a bad idea.\n\n" +
            "This check looks for menu XML files that contain more than two `always` " +
            "actions, or some `always` actions and no `ifRoom` actions. In Java code, " +
            "it looks for projects that contain references to `MenuItem.SHOW_AS_ACTION_ALWAYS` " +
            "and no references to `MenuItem.SHOW_AS_ACTION_IF_ROOM`.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(
                    AlwaysShowActionDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE)
            )
    );

    private final List<Location> mAlwaysLocations = new ArrayList<>();
    private boolean mHasIfRoom = false;

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mAlwaysLocations.clear();
        mHasIfRoom = false;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        if (context.getFolderType() != ResourceFolderType.MENU) {
            return;
        }
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        List<Attr> alwaysAttrs = new ArrayList<>();
        List<Attr> ifRoomAttrs = new ArrayList<>();
        collectShowAsActionAttributes(root, alwaysAttrs, ifRoomAttrs);

        if (alwaysAttrs.size() > 2) {
            for (Attr attr : alwaysAttrs) {
                context.report(ISSUE, attr, context.getLocation(attr),
                        "Do not use \"`always`\" more than twice (used " + alwaysAttrs.size() +
                        " times in this menu); prefer \"`ifRoom`\"");
            }
        } else if (!alwaysAttrs.isEmpty() && ifRoomAttrs.isEmpty()) {
            for (Attr attr : alwaysAttrs) {
                context.report(ISSUE, attr, context.getLocation(attr),
                        "Prefer \"`ifRoom`\" instead of \"`always`\"");
            }
        }
    }

    private void collectShowAsActionAttributes(Element element, List<Attr> alwaysAttrs, List<Attr> ifRoomAttrs) {
        if ("item".equals(element.getTagName())) {
            NamedNodeMap attributes = element.getAttributes();
            for (int i = 0; i < attributes.getLength(); i++) {
                Node attrNode = attributes.item(i);
                if (attrNode instanceof Attr) {
                    Attr attr = (Attr) attrNode;
                    if ("showAsAction".equals(attr.getLocalName())) {
                        String value = attr.getValue();
                        if (value != null) {
                            String[] parts = value.split("\\|");
                            for (String part : parts) {
                                String trimmed = part.trim();
                                if ("always".equals(trimmed)) {
                                    alwaysAttrs.add(attr);
                                } else if ("ifRoom".equals(trimmed)) {
                                    ifRoomAttrs.add(attr);
                                }
                            }
                        }
                    }
                }
            }
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                collectShowAsActionAttributes((Element) child, alwaysAttrs, ifRoomAttrs);
            }
        }
    }

    @Override
    public List<String> getApplicableReferenceNames() {
        return Arrays.asList("SHOW_AS_ACTION_ALWAYS", "SHOW_AS_ACTION_IF_ROOM");
    }

    @Override
    public void visitReference(@NonNull JavaContext context, @NonNull UReferenceExpression reference, @NonNull PsiElement referenced) {
        if (referenced instanceof PsiField) {
            PsiField field = (PsiField) referenced;
            PsiClass containingClass = field.getContainingClass();
            if (containingClass != null && "android.view.MenuItem".equals(containingClass.getQualifiedName())) {
                String name = field.getName();
                if ("SHOW_AS_ACTION_ALWAYS".equals(name)) {
                    mAlwaysLocations.add(context.getLocation(reference));
                } else if ("SHOW_AS_ACTION_IF_ROOM".equals(name)) {
                    mHasIfRoom = true;
                }
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (!mAlwaysLocations.isEmpty() && !mHasIfRoom) {
            for (Location location : mAlwaysLocations) {
                context.report(ISSUE, location, "Prefer \"`SHOW_AS_ACTION_IF_ROOM`\" instead of \"`SHOW_AS_ACTION_ALWAYS`\"");
            }
        }
    }
}