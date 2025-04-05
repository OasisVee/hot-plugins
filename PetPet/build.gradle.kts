version = "1.2.0"
description = "Pet pet"

aliucord {
    author("Alyxia", 465702500146610176L)

    changelog.set(
            """
            # 1.2.0
            * Completely rewritten implementation
            * Now generates GIFs locally like the TypeScript version
            * Added options for delay, resolution, and no-server-pfp
            * Uses frames from GitHub repository
            
            # 1.1.8   
            * Change api
             
            # 1.1.7   
            * Change api
             
            # 1.1.6
            * Change api
            
            # 1.1.5
            * Convert from Java to Kotlin
                
            # 1.1.4
            * Support For V96
                
            """.trimIndent()
    )
}

// Add Coil and other dependencies
dependencies {
    implementation("io.coil-kt:coil:1.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.0")
    implementation("com.github.bumptech.glide:glide:4.13.2")
}
