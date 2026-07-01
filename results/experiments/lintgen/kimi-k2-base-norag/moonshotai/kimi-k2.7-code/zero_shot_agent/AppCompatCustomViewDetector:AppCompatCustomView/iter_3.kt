class AppCompatCustomViewDetector : Detector(), Detector.ClassScanner {

    override fun getApplicableCallOwners(): List<String>? = null

    override fun checkSameOwner(): Boolean = false

    override fun visitClass(context: ClassContext, classNode: ClassNode) {
        ...
    }

    override fun visitMethod(context: ClassContext, methodNode: MethodNode) {}
    override fun visitField(context: ClassContext, fieldNode: FieldNode) {}
    override fun visitInstruction(
        context: ClassContext,
        methodNode: MethodNode,
        instruction: AbstractInsnNode
    ) {}
}