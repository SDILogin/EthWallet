# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

EthWallet is an Android application built using Kotlin and Jetpack Compose that serves as an Ethereum wallet. The app is in the early stages of development.

## Build System

This project uses Gradle with Kotlin DSL for the build system:

- **Build the project**: `./gradlew build`
- **Run tests**: `./gradlew test`
- **Run a single test**: `./gradlew test --tests "me.sdi.ethwallet.TestClassName.testMethodName"`
- **Run instrumented tests**: `./gradlew connectedAndroidTest`
- **Run lint checks**: `./gradlew lint`
- **Clean build files**: `./gradlew clean`
- **Install debug APK**: `./gradlew installDebug`
- **Generate APK**: `./gradlew assembleDebug` or `./gradlew assembleRelease`

## Development Environment

- The project uses Kotlin 2.0.21
- Minimum SDK: 24 (Android 7.0)
- Target SDK: 35
- Uses Jetpack Compose for UI
- JVM target: Java 11

## Architecture Guidelines

As the project grows, consider implementing:

1. **Clean Architecture** with layers:
   - UI Layer (Compose UI components)
   - ViewModel Layer (UI state management)
   - Domain Layer (business logic)
   - Data Layer (repositories and data sources)

2. **For Ethereum integration**:
   - Consider using Web3j or similar libraries for Ethereum blockchain interactions
   - Implement secure key storage using Android Keystore

3. **Testing Strategy**:
   - Unit tests for business logic
   - UI tests using Compose testing framework
   - Integration tests for blockchain interactions