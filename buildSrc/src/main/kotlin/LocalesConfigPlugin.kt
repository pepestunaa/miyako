import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import java.io.File

fun Project.registerLocalesConfigTask(outputResourceDir: File): TaskProvider<Task> {
    return tasks.register("generateLocalesConfig") {
        val content = """
<?xml version="1.0" encoding="utf-8"?>
<locale-config xmlns:android="http://schemas.android.com/apk/res/android">
   <locale android:name="en"/>
</locale-config>
""".trimIndent()

        outputResourceDir.resolve("xml/locales_config.xml").apply {
            parentFile.mkdirs()
            writeText(content)
        }
    }
}
