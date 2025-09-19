DASHBOARD4 - JavaFX UI module

This module is a thin JavaFX UI that calls the app-core APIs. It's intentionally minimal for Phase1.

Build and run (Windows PowerShell):

1) Build:

   mvn -f dashboard4-ui-javafx clean package

2) Run (uses javafx-maven-plugin):

   mvn -f dashboard4-ui-javafx javafx:run

Note: You may need to set JAVA_HOME to a JDK 21 that supports JavaFX modules, or add OpenJFX SDK as needed.
