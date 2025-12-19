# NTAG424 Library Setup

This directory should contain the `ntag424-java` library JAR file.

## Prerequisites: Install Maven

If you don't have Maven installed, install it first:

**On macOS (using Homebrew):**
```bash
brew install maven
```

**Verify installation:**
```bash
mvn -version
```

## Steps to Add the Library:

1. **Clone or download the ntag424-java repository:**
   ```bash
   git clone https://github.com/johnnyb/ntag424-java.git
   cd ntag424-java
   ```

2. **Build the library:**
   ```bash
   mvn clean install
   ```

3. **Copy the JAR file to this directory:**
   ```bash
   cp target/ntag424-*.jar /Users/andy/code/demo/NFCTagWriter_Android/app/libs/
   ```

4. **Sync Gradle in Android Studio**

The JAR file should be named something like `ntag424-1.0.0.jar` (version may vary).

## Alternative: Download Pre-built JAR

If you have access to a pre-built JAR file, simply place it in this directory with a `.jar` extension.

## Note

The project will not compile until the JAR file is added to this directory.

