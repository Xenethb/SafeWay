// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.2.2")
        classpath("com.google.gms:google-services:4.3.15")  // Add Google services classpath here
    }
}

plugins {
    // Keep this or remove if you want, usually for root projects it's empty or minimal
    id("com.android.application") version "8.2.2" apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
