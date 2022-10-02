plugins {
    // Kotlinを使用するためのプラグイン
    kotlin("jvm") version "1.7.10"
    // Fabricを使用するためのプラグイン
    id("fabric-loom") version "1.0-SNAPSHOT"
    // Maven
    `maven-publish`
}

// グループ定義
group = project.properties["maven_group"].toString()
// バージョン定義
version = project.properties["mod_version"].toString()

repositories {
    // Add repositories to retrieve artifacts from in here.
    // You should only use this when depending on other mods because
    // Loom adds the essential maven repositories to download Minecraft and libraries from automatically.
    // See https://docs.gradle.org/current/userguide/declaring_repositories.html
    // for more information about repositories.
}

dependencies {
    // バージョンを変更するためには gradle.properties を変更
    // マイクラ
    minecraft("com.mojang:minecraft:${project.properties["minecraft_version"].toString()}")
    // マッピング
    mappings("net.fabricmc:yarn:${project.properties["yarn_mappings"].toString()}:v2")
    // Fabric Loader
    modImplementation("net.fabricmc:fabric-loader:${project.properties["loader_version"].toString()}")
    // Fabric API
    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.properties["fabric_version"].toString()}")
    // Kotlin
    modImplementation("net.fabricmc:fabric-language-kotlin:1.8.3+kotlin.1.7.10")
}

val targetJavaVersion = 17
java {
    // Javaのバージョンを設定
    val javaVersion = JavaVersion.toVersion(targetJavaVersion)
    if (JavaVersion.current() < javaVersion) {
        toolchain.languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
    }
    base.archivesName.set(project.properties["archives_base_name"].toString())
    // LoomはRemapSourcesJarタスクと"build"タスクにsourcesJarを自動的に添付します。
    // この行を削除すると、ソースが生成されません。
    withSourcesJar()
}

tasks {
    // fabric.mod.jsonの中にバージョンを埋め込む
    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("fabric.mod.json") {
            expand(props)
        }
    }

    withType<JavaCompile> {
        // システムのデフォルトがどんな設定でもエンコードをUTF-8に固定します。
        // これは、特別な文字が正しく表示されない一部のエッジケースを修正します
        // 詳細は http://yodaconditions.net/blog/fix-for-java-file-encoding-problems-with-gradle.html
        // もしJavadocが生成されるなら、それはそれ自身のタスクで指定する必要があります。
        options.encoding = "UTF-8"
        if (targetJavaVersion >= 10 || JavaVersion.current().isJava10Compatible) {
            options.release.set(targetJavaVersion)
        }
    }

    jar {
        // ライセンス
        from("LICENSE") {
            rename { "${it}_${project.base.archivesName}" }
        }
    }
}

// Maven公開用設定
publishing {
    publications {
        register("mavenJava", MavenPublication::class) {
            from(components["java"])
        }
    }

    // See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
    repositories {
        // Add repositories to publish to here.
        // Notice: This block does NOT have the same function as the block in the top level.
        // The repositories here will be used for publishing your artifact, not for
        // retrieving dependencies.
    }
}
