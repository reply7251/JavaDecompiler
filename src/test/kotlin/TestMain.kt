
import me.hellrevenger.javadecompiler.ui.MainWindow
import java.io.File

fun main() {
    me.hellrevenger.javadecompiler.main()

    MainWindow.fileTree.addFile(File("build/libs/JavaDecompiler.jar"))
}