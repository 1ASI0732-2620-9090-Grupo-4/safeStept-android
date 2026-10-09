# SafeStep Android (piloto académico)

Cliente nativo Kotlin/Jetpack Compose para los flujos de usuario de SafeStep: registro/inicio de sesión, simulaciones, envío de resultados, progreso y catálogo. La administración permanece en la aplicación web.

## Ejecutar

1. Abrir el proyecto con Android Studio (SDK 37, JDK 21 o compatible) o usar `./gradlew assembleDebug`.
2. Iniciar el backend y PostgreSQL según su README.
3. En el emulador, usar `http://10.0.2.2:8092/api/v1`; en un dispositivo físico, indicar la URL HTTPS de una API de prueba alcanzable.
4. Ejecutar `./gradlew testDebugUnitTest` y `./gradlew connectedDebugAndroidTest` con un emulador/dispositivo conectado.

En Windows, la ruta del workspace contiene `Diseño`. La compilación APK funciona con `android.overridePathCheck=true`, pero el cargador JUnit de esta instalación no encuentra las clases de prueba desde esa ruta. Las pruebas se ejecutaron correctamente al montar temporalmente el workspace en una unidad con ruta ASCII (`subst S: C:\Users\melga\Desktop\TrabajoFinalDiseñoExperimentos`) y llamar a Gradle desde `S:\safestept-android`. Retirar la unidad temporal con `subst S: /D` cuando deje de usarse.

El token de acceso se mantiene solo en memoria. La app no incorpora claves de Stripe ni credenciales de base de datos. El tráfico HTTP sin cifrar se habilita únicamente en la variante `debug` para el desarrollo local con `10.0.2.2`; la variante de publicación requiere HTTPS.

El repositorio GitHub, el APK firmado y las capturas de pruebas deben agregarse al capítulo 5 cuando existan. Esta carpeta local no demuestra por sí misma publicación ni distribución.
