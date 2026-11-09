# SafeStep Android

Aplicación nativa (Kotlin y Jetpack Compose) de SafeStep para practicar primeros auxilios con simulaciones. Comparte la API REST con la aplicación web y cubre todos los flujos del jugador: registro e ingreso con renovación del token, recuperación de contraseña, perfil, simulaciones con filtros y retroalimentación por respuesta, recompensas, progreso, misiones, insignias, ranking, estadísticas, certificados, tienda, kits, cupones, carrito, pedidos y pago con Stripe. La administración del catálogo sigue siendo solo de la aplicación web.

## Qué hay dentro

| Carpeta (`app/src/main/java/com/safestep/mobile`) | Contenido |
| --- | --- |
| `core/network` | Clases de datos de la API, interfaces Retrofit, cliente HTTP con el token de acceso y renovación automática ante un 401 |
| `core/session` | Sesión del usuario; los tokens se guardan cifrados con una clave AES-GCM del Android Keystore |
| `core` | Repositorios y `AppContainer` (la composición se hace a mano) |
| `iam`, `profile`, `home`, `simulation`, `gamification`, `commerce` | Una carpeta por contexto, con sus pantallas y `ViewModel` |
| `ui` | Tema (la paleta de la web, claro y oscuro) y componentes comunes |

**La recompensa la calcula el servidor.** La aplicación solo envía el intento (puntaje y pasos) y muestra lo que el servidor acreditó, leído de su resumen: gana monedas con 70 % o más de aciertos y cada repetición aprobada de la misma simulación paga la mitad de la anterior.

**Pago.** Se crea el pedido con el cupón elegido y el pago se completa en la página de Stripe, que se abre en una pestaña del navegador. Al volver, la aplicación pregunta al servidor si el pago se confirmó; también hay un botón para verificarlo a mano. La aplicación nunca ve los datos de la tarjeta.

## Ejecutar

Requisitos: Android Studio (JBR 21) o JDK 21, SDK 37 y un emulador o dispositivo.

1. Levantar el backend en el puerto 8092 y, si se quieren las imágenes, la aplicación web en el 4200 con `ng serve --host 0.0.0.0`.
2. Variantes: `debug` usa `http://10.0.2.2:8092/api/v1` (el emulador ve el equipo anfitrión y se permite HTTP solo aquí); `release` usa la API publicada con HTTPS. El origen de las imágenes se define en `ASSETS_BASE_URL` (`app/build.gradle.kts`); vacío muestra marcadores de posición.
3. `./gradlew assembleDebug` genera `app/build/outputs/apk/debug/app-debug.apk`.

## Pruebas

```bash
./gradlew testDebugUnitTest          # 25 pruebas unitarias: cliente API (MockWebServer), sesión, flujo de simulación, carrito y validación
./gradlew connectedDebugAndroidTest  # 9 pruebas de sistema en el emulador contra la API real
```

Las pruebas de sistema (`SystemFlowTest`) usan la aplicación real y el backend real: crean un jugador, completan simulaciones, canjean un cupón y comprueban el carrito con descuento. `EvidenceTourTest` recorre las 21 pantallas y guarda una captura de cada una en `Android/data/com.safestep.mobile/files/screens` del dispositivo (`adb pull`).

En Windows, la ruta del espacio de trabajo contiene `Diseño`: la compilación funciona gracias a `android.overridePathCheck=true`, pero el cargador de JUnit no encuentra las clases de prueba. Se resuelve montando la carpeta en una unidad con ruta sin tildes antes de ejecutar Gradle:

```bash
subst S: "C:\Users\<usuario>\OneDrive\Desktop\TrabajoFinalDiseñoExperimentos"
cd /s/safeStept-android && ./gradlew testDebugUnitTest
subst S: /D
```

En el emulador con renderizado por software (`-gpu swiftshader_indirect`) la interfaz del sistema puede dejar de responder y tapar las capturas; con `-gpu host` y 4 núcleos funciona bien.

## Límites conocidos

- El pago con Stripe se verificó con un servidor simulado (pruebas unitarias del cliente); la prueba real necesita las claves de prueba de Stripe en el backend.
- No hay APK firmado ni publicación; esta carpeta no demuestra distribución.
- Las imágenes de productos y simulaciones se sirven desde la aplicación web (`ASSETS_BASE_URL`); sin ella se ven marcadores de posición.
- Los textos de la interfaz están en español e inglés; el contenido que viene del servidor (simulaciones, misiones, insignias) está en español.
