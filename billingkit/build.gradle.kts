import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("maven-publish")
}

android {
    namespace = "com.shibaprasadsahu.billingkit"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 21


        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}

dependencies {
    // Lifecycle for lifecycle-aware callbacks
    implementation(libs.androidx.lifecycle.runtime.ktx)

    api(libs.billing.ktx)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // Testing
    testImplementation(libs.junit)
}


// Maven Publishing for JitPack
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                groupId = "com.github.shibaprasadsahu"
                artifactId = "billingkit"
                version = "0.1-alpha03"

                pom {
                    name.set("BillingKit")
                    description.set("Modern Android billing library for Google Play subscriptions with Flow and callback support")
                    url.set("https://github.com/shibaprasadsahu/BillingKit")

                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }

                    scm {
                        connection.set("scm:git:git://github.com/shibaprasadsahu/BillingKit.git")
                        developerConnection.set("scm:git:ssh://github.com/shibaprasadsahu/BillingKit.git")
                        url.set("https://github.com/shibaprasadsahu/BillingKit")
                    }
                }
            }
        }
    }
}