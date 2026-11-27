// CI only: Maven Central answers "429 Too Many Requests" to shared runners; Google's mirror of it
// serves the same files. Installed into ~/.gradle/init.d by the workflows; locally pass it with
// --init-script when Maven Central refuses. The build files are unchanged (F-Droid builds as before).
val mirror = "https://maven-central.storage-download.googleapis.com/maven2/"
fun RepositoryHandler.useMirror() = all {
    if (this is MavenArtifactRepository && url.host in setOf("repo.maven.apache.org", "repo1.maven.org")) url = uri(mirror)
}
beforeSettings {
    pluginManagement.repositories.useMirror()
    dependencyResolutionManagement.repositories.useMirror()
}
settingsEvaluated {
    pluginManagement.repositories.useMirror()
    dependencyResolutionManagement.repositories.useMirror()
}
allprojects { repositories.useMirror(); buildscript.repositories.useMirror() }
