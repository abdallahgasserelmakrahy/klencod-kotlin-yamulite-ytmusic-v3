// Vendored copy of NewPipeExtractor v0.26.5 (GPL-3.0-or-later, see LICENSE).
// Only change vs upstream: utils/Utils.java no longer calls URLEncoder.encode/URLDecoder.decode
// with a Charset argument — those overloads exist only on Android 13+ and crashed older phones with
// "No static method encode(String, Charset) in class URLEncoder".
plugins {
    `java-library`
    alias(libs.plugins.google.protobuf)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(11)
}

// Protobuf files would uselessly end up in the JAR otherwise
tasks.jar {
    exclude("**/*.proto")
    includeEmptyDirs = false
}

dependencies {
    implementation(libs.newpipe.nanojson)
    implementation(libs.jsoup)
    implementation(libs.google.jsr305)
    implementation(libs.google.protobuf)
    implementation(libs.mozilla.rhino.core)
    implementation(libs.mozilla.rhino.engine)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.lib.get()}"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                named("java") {
                    option("lite")
                }
            }
        }
    }
}
