import java.io.ByteArrayOutputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

dependencies {
    implementation(project(":ext"))
    val libVersion: String by project
    compileOnly("com.github.brahmkshatriya:echo:$libVersion")
}

val extType: String by project
val extId: String by project
val extClass: String by project

val extIconUrl: String? by project
val extName: String by project
val extDescription: String? by project

val extAuthor: String by project
val extAuthorUrl: String? by project

val extRepoUrl: String? by project
val extUpdateUrl: String? by project

val gitHash = execute("git", "rev-parse", "HEAD").take(7)
val gitCount = execute("git", "rev-list", "--count", "HEAD").toInt()
val verCode = gitCount
val verName = "v$gitHash"

tasks.register("uninstall") {
    exec {
        isIgnoreExitValue = true
        executable(android.adbExecutable)
        args("shell", "pm", "uninstall", android.defaultConfig.applicationId!!)
    }
}

android {
    namespace = "dev.brahmkshatriya.echo.extension"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.brahmkshatriya.echo.extension.$extId"
        minSdk = 24
        targetSdk = 35

        manifestPlaceholders.apply {
            put("type", "dev.brahmkshatriya.echo.${extType}")
            put("id", extId)
            put("class_path", "dev.brahmkshatriya.echo.extension.${extClass}")
            put("version", verName)
            put("version_code", verCode.toString())
            extIconUrl?.let { put("icon_url", it) }
            put("app_name", "Echo : $extName Extension")
            put("name", extName)
            extDescription?.let { put("description", it) }
            put("author", extAuthor)
            extAuthorUrl?.let { put("author_url", it) }
            extRepoUrl?.let { put("repo_url", it) }
            extUpdateUrl?.let { put("update_url", it) }
        }
        resValue("string", "id", extId)
        resValue("string", "class_path", "$namespace.${extClass}")

        versionName = verName
        resValue("string", "version", verName)
        versionCode = verCode
        resValue("string", "version_code", verCode.toString())

        extIconUrl?.let { resValue("string", "icon_url", it) }
        resValue("string", "app_name", "Echo : $extName Extension")
        resValue("string", "name", extName)
        description?.let { resValue("string", "description", it) }

        resValue("string", "author", extAuthor)
        extAuthorUrl?.let { resValue("string", "author_url", it) }

        extRepoUrl?.let { resValue("string", "repo_url", it) }
        extUpdateUrl?.let { resValue("string", "update_url", it) }
    }

    buildTypes {
        release {
            isDebuggable = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

fun execute(vararg command: String): String {
    val outputStream = ByteArrayOutputStream()
    project.exec {
        commandLine(*command)
        standardOutput = outputStream
    }
    return outputStream.toString().trim()
}