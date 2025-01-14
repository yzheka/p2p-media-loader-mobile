# P2P Media Loader Mobile

![Platform: Android](https://img.shields.io/badge/platform-Android-blue) 
[![Kotlin](https://img.shields.io/badge/kotlin-2.1.0-blue.svg?logo=kotlin)](https://kotlinlang.org) 
[![JitPack](https://jitpack.io/v/Novage/p2p-media-loader-mobile.svg)](https://jitpack.io/#Novage/p2p-media-loader-mobile)
[![Build Status](https://img.shields.io/github/actions/workflow/status/Novage/p2p-media-loader-mobile/check-build.yml)](https://github.com/Novage/p2p-media-loader-mobile/actions) 

A Kotlin library for peer-to-peer media loading, designed to enhance streaming performance and reduce server load. Seamlessly integrate P2PML into your Android application with minimal setup.

## Features
- Efficient P2P content sharing for HLS
- Simple integration and configuration
- Improved streaming performance with reduced server bandwidth usage

---

## Getting Started

### Step 1: Add JitPack to Your Project

To include the P2P Media Loader Mobile library, first, configure `dependencyResolutionManagement` in your **`settings.gradle`** file:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}
```

### Step 2: Add the Library Dependency

Add the following implementation line to your **`build.gradle`** (app module):

```kotlin
implementation("com.github.Novage:p2p-media-loader-mobile:main-d71e8e4552-1")
```

### Step 3: Configure the AndroidManifest

Ensure that your app has the necessary permissions and configurations by updating the **`AndroidManifest.xml`** file:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- Add internet access permission -->
    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:networkSecurityConfig="@xml/network_security_config"
        ... >
        ...
    </application>
</manifest>
```

### Step 4: Allow Localhost Connections

Create or update the **`res/xml/network_security_config.xml`** file to allow localhost connections:

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">127.0.0.1</domain>
    </domain-config>
</network-security-config>
```

Make sure to reference this file in the `<application>` tag of your **`AndroidManifest.xml`**.

---

## Usage

Once you've completed the setup, P2P Media Loader is ready to use in your application!

