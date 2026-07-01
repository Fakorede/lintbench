public class DuplicateResourceDetector extends Detector implements XmlScanner, ResourceFolderScanner {
    public static final Issue DUPLICATE_DEFINITION = Issue.create(
            "DuplicateDefinition",
            "Duplicate resource definitions",
            "You can define a resource ...",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(DuplicateResourceDetector.class, Scope.ALL_RESOURCES_SCOPE));

    private Map<File, Set<String>> mReported;
    private Map<File, Map<String, Location>> mValueLocations;

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mReported = new HashMap<>();
        mValueLocations = new HashMap<>();
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    public void checkFolder(@NonNull ResourceContext context, @NonNull String folderName) {
        ResourceFolderType folderType = ResourceFolderType.getFolderType(folderName);
        if (folderType == null || folderType == ResourceFolderType.VALUES) {
            return;
        }

        File[] files = context.file.listFiles();
        if (files == null) {
            return;
        }

        Map<String, List<File>> nameToFiles = new HashMap<>();
        for (File file : files) {
            if (file.isFile()) {
                String name = getResourceName(file);
                if (name != null) {
                    nameToFiles.computeIfAbsent(name, k -> new ArrayList<>()).add(file);
                }
            }
        }

        for (Map.Entry<String, List<File>> entry : nameToFiles.entrySet()) {
            List<File> list = entry.getValue();
            if (list.size() > 1) {
                File first = list.get(0);
                Location firstLocation = Location.create(first);
                for (int i = 1; i < list.size(); i++) {
                    File duplicate = list.get(i);
                    Location location = Location.create(duplicate);
                    String message = String.format("Duplicate resource `%1$s` appears more than once in `%2$s`", entry.getKey(), folderName);
                    context.report(DUPLICATE_DEFINITION, location, message, ...);
                }
            }
        }
    }

    private static String getResourceName(File file) {
        String name = file.getName();
        if (name.endsWith(".9.png")) {
            return name.substring(0, name.length() - ".9.png".length());
        }
        int dot = name.lastIndexOf('.');
        return dot == -1 ? name : name.substring(0, dot);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.VALUES) {
            return;
        }
        if (!"resources".equals(element.getParentNode().getNodeName())) {
            return;
        }
        String tag = element.getTagName();
        String name = element.getAttributeNS(null, "name");
        if (name.isEmpty()) {
            return;
        }
        if ("item".equals(tag)) {
            tag = element.getAttributeNS(null, "type");
        }
        if (tag == null || tag.isEmpty()) {
            return;
        }
        File folder = context.file.getParentFile();
        Map<String, Location> map = mValueLocations.computeIfAbsent(folder, k -> new HashMap<>());
        Location existing = map.get(name + "/" + tag);
        if (existing != null) {
            context.report(...);
        } else {
            map.put(name + "/" + tag, context.getLocation(element));
        }
    }
}