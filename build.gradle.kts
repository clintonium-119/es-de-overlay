// AGP's own build libraries, raised past security advisories in the versions it ships. Each line can go once AGP's
// version passes it (./gradlew buildEnvironment shows what it brings).
buildscript {
    dependencies {
        constraints {
            classpath("org.bouncycastle:bcprov-jdk18on:1.86")
            classpath("org.bouncycastle:bcpkix-jdk18on:1.86")
            classpath("org.bouncycastle:bcutil-jdk18on:1.86")
            classpath("com.google.guava:guava:33.8.0-jre")
            classpath("org.jdom:jdom2:2.0.6.1")
            classpath("org.bitbucket.b_c:jose4j:0.9.7")
            classpath("org.apache.commons:commons-lang3:3.21.0")
        }
    }
}

plugins {
    id("com.android.application") version "9.5.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
