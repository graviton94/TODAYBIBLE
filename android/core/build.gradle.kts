plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin { jvmToolchain(17) }

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    // 테스트는 앱 자산의 본문(app/src/main/assets/bible)을 직접 읽는다
    systemProperty("assetsDir", projectDir.resolve("../app/src/main/assets").absolutePath)
}
