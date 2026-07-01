public class TranslucentViewDetector extends Detector implements XmlScanner {
    public static final Issue ISSUE = Issue.create(
        "TranslucentOrientation",
        "Mixing screenOrientation and translucency",
        "Specifying a fixed screenOrientation with a translucent theme isn't supported ...",
        Category.CORRECTNESS, 6, Severity.ERROR,
        new Implementation(TranslucentViewDetector.class, Scope.MANIFEST_AND_RESOURCE_SCOPE));

    private static final int ANDROID_O = 26;
    private static final String WINDOW_IS_TRANSLUCENT = "android:windowIsTranslucent";

    private Map<String, Style> mStyles = new HashMap<>();
    private List<Activity> mActivities = new ArrayList<>();
    private String mApplicationTheme;

    private static class Style {
        final String parent;
        final boolean translucent;
        Style(String parent, boolean translucent) { ... }
    }
    private static class Activity {
        final Location location;
        final String orientation;
        final String theme;
        Activity(Location location, String orientation, String theme) { ... }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_ACTIVITY, TAG_APPLICATION, TAG_STYLE);
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        mStyles.clear();
        mActivities.clear();
        mApplicationTheme = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (TAG_STYLE.equals(tag)) {
            String name = element.getAttribute(ATTR_NAME);
            if (name.isEmpty()) return;
            String parent = element.getAttribute(ATTR_PARENT);
            if (parent.isEmpty()) {
                int dot = name.lastIndexOf('.');
                parent = dot > 0 ? name.substring(0, dot) : null;
            }
            boolean translucent = false;
            Node child = element.getFirstChild();
            while (child != null) {
                if (child.getNodeType() == Node.ELEMENT_NODE && child instanceof Element && TAG_ITEM.equals(((Element) child).getTagName())) {
                    String attrName = ((Element) child).getAttribute(ATTR_NAME);
                    if (WINDOW_IS_TRANSLUCENT.equals(attrName) || "windowIsTranslucent".equals(attrName)) {
                        String value = child.getTextContent().trim();
                        if ("true".equals(value)) {
                            translucent = true;
                            break;
                        }
                    }
                }
                child = child.getNextSibling();
            }
            mStyles.put(name, new Style(parent, translucent));
        } else if (TAG_ACTIVITY.equals(tag)) {
            String orientation = element.getAttributeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION);
            if (orientation.isEmpty()) return;
            String theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME);
            if (theme.isEmpty()) theme = null;
            mActivities.add(new Activity(context.getLocation(element), orientation, theme));
        } else if (TAG_APPLICATION.equals(tag)) {
            String theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME);
            mApplicationTheme = theme.isEmpty() ? null : theme;
        }
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        int targetSdk = context.getProject().getTargetSdkVersion() != null ? context.getProject().getTargetSdkVersion().getFeatureLevel() : 0;
        if (targetSdk < ANDROID_O) return;
        for (Activity activity : mActivities) {
            String orientation = activity.orientation;
            if ("unspecified".equals(orientation) || "behind".equals(orientation)) continue;
            String theme = activity.theme != null ? activity.theme : mApplicationTheme;
            if (theme == null) continue;
            if (isTranslucent(theme)) {
                context.report(ISSUE, activity.location, "Should not specify a fixed screenOrientation with a translucent theme");
            }
        }
    }

    private boolean isTranslucent(String theme) { return isTranslucent(theme, new HashSet<>()); }

    private boolean isTranslucent(String theme, Set<String> seen) {
        if (theme.startsWith("@style/")) {
            String name = theme.substring("@style/".length());
            return isTranslucent(name, seen);
        } else if (theme.startsWith("@android:style/")) {
            if (theme.contains("Translucent")) return true;
            String name = theme.substring("@android:style/".length());
            return isTranslucent(name, seen);
        } else if (theme.startsWith("style/")) {
            return isTranslucent(theme.substring("style/".length()), seen);
        } else if (theme.startsWith("android:style/")) {
            if (theme.contains("Translucent")) return true;
            return isTranslucent(theme.substring("android:style/".length()), seen);
        } else if (theme.startsWith("@")) {
            return false;
        } else {
            // plain name
            if (!seen.add(theme)) return false;
            Style style = mStyles.get(theme);
            if (style == null) return false;
            if (style.translucent) return true;
            if (style.parent == null) return false;
            return isTranslucent(style.parent, seen);
        }
    }
}