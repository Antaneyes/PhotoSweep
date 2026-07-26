# PhotoSweep

PhotoSweep es una aplicación Android privada para revisar una fototeca de
Google Photos con una interacción similar a Tinder: deslizar para conservar o
añadir a una cesta y confirmar después el envío de esa cesta a la papelera.

La versión actual es **0.2.0** y está orientada a dispositivos `arm64-v8a`.

## Características

- Inicio de sesión de Google dentro de un perfil privado de GeckoView.
- El navegador permanece oculto después de autenticar la cuenta.
- Indexación paginada de toda la fototeca de Google Photos.
- Mazo de hasta 10.000 elementos pendientes, seleccionado aleatoriamente en
  cada inicio de la aplicación.
- Precarga de las primeras tarjetas al abrir la app y de las siguientes
  mientras se revisa el mazo.
- Swipe a la derecha para conservar y a la izquierda para añadir a la cesta.
- Botones alternativos para realizar ambas acciones sin gestos.
- Historial de deshacer múltiple durante la sesión.
- Fotografías ajustadas para rellenar la tarjeta inicialmente.
- Pellizco con dos dedos para ampliar, alejar y desplazar la imagen; un dedo
  queda reservado para el swipe.
- Reproducción de vídeos con Media3 ExoPlayer, sonido, controles y bucle.
- Cesta persistente con confirmación doble antes de modificar Google Photos.
- Galerías de Cesta y Conservadas accesibles desde la pantalla principal.
- Visor a pantalla completa con navegación horizontal entre fotos y vídeos.
- Miniaturas autenticadas con cola, caché y reintentos.
- Navegación mediante el gesto Atrás de Android sin abandonar prematuramente
  la aplicación.
- Splash y pantalla de preparación nativos con el diseño oscuro de PhotoSweep.
- Sin backend, root, servicio de Accesibilidad ni navegador externo.

## Qué ocurre al borrar

PhotoSweep no elimina definitivamente los elementos. La confirmación de la
cesta solicita a Google Photos que los mueva a su papelera. Tras una respuesta
correcta:

- desaparecen de la cesta de PhotoSweep;
- dejan de formar parte del pool pendiente para el swipe;
- permanecen recuperables desde la papelera de Google Photos hasta que Google
  los elimine por su política de retención o el usuario vacíe la papelera.

La aplicación nunca vacía la papelera.

## Arquitectura

- **Kotlin + Jetpack Compose** para toda la interfaz nativa.
- **GeckoView** como sesión web privada y motor de autenticación.
- **WebExtension integrada** en `app/src/main/assets/photosweep` para comunicarse
  con Google Photos desde el contexto autenticado.
- **SQLite** mediante `MediaDatabase` para el índice y los estados locales.
- **Media3 ExoPlayer** para la reproducción de vídeo.
- **Coil 3** para mostrar las miniaturas entregadas por el puente autenticado.

Los principales estados locales son:

- `UNSEEN`: pendiente de revisión.
- `KEPT`: conservado.
- `BASKET`: preparado para enviar a la papelera.
- `TRASHED`: Google aceptó el movimiento a la papelera.
- `FAILED`: el último intento de moverlo falló.

## Limitación importante

Google no ofrece una API pública que permita recorrer una fototeca existente y
enviar libremente sus elementos a la papelera. PhotoSweep utiliza llamadas web
internas y no documentadas de Google Photos (`EzkLib`, `VrseUb` y `XwAOJf`).

Por ello:

- es un proyecto privado y experimental;
- una actualización de Google Photos puede romper la integración;
- no debe publicarse en Play Store como si usara una API oficial;
- conviene probar cualquier versión nueva con elementos desechables.

## Requisitos

- JDK 17 o superior (el entorno de desarrollo actual usa JDK 21).
- Android SDK 36 o superior.
- Android con arquitectura `arm64-v8a`.
- Conexión a Internet.
- Una cuenta de Google con Google Photos.

## Compilar y verificar

El proyecto incluye Gradle Wrapper:

```bash
./gradlew assembleDebug test lint
```

La APK resultante se genera en:

```text
app/build/outputs/apk/debug/app-debug.apk
```

GeckoView hace que la APK de depuración sea grande. Esta variante está limitada
a `arm64-v8a` para reducir parcialmente su tamaño.

### APK release para compartir

La compilación release requiere un archivo local `signing.properties` (ignorado
por Git) con estas claves:

```properties
storeFile=/ruta/segura/photosweep-release.jks
storePassword=...
keyAlias=photosweep
keyPassword=...
```

Después se genera la APK firmada y optimizada con:

```bash
./gradlew clean assembleRelease
```

El archivo para distribuir queda en:

```text
app/build/outputs/apk/release/app-release.apk
```

La clave debe conservarse de forma segura: todas las futuras actualizaciones
distribuidas necesitan estar firmadas con la misma clave. Una instalación
`debug` existente no se puede actualizar directamente con la APK `release`
porque sus firmas son diferentes; primero hay que desinstalar la variante
debug, lo que borra sus datos locales.

## Instalar mediante ADB

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`-r` conserva la sesión, el índice y las decisiones locales. Para simular una
instalación completamente nueva:

```bash
adb shell pm clear com.josh.photosweep
```

Este último comando borra todos los datos locales de PhotoSweep, incluida la
sesión de Google, pero no modifica las fotos almacenadas en Google Photos.

## Primer uso

1. Abre PhotoSweep.
2. Pulsa **Abrir inicio de sesión**.
3. Autentica la cuenta y abre Google Photos.
4. De vuelta en PhotoSweep, pulsa **Indexar toda la fototeca**.
5. Espera a que termine la paginación.
6. Pulsa **Empezar a deslizar**.
7. Revisa la cesta antes de confirmar cualquier movimiento a la papelera.

## Prueba segura recomendada

1. Crea y respalda una foto y un vídeo desechables.
2. Comprueba que ambos aparecen, cargan y se reproducen correctamente.
3. Añade únicamente esos elementos a la cesta.
4. Abre cada uno desde la galería de la cesta.
5. Confirma el movimiento.
6. Comprueba en Google Photos que aparecen en la papelera y que se pueden
   restaurar.

No se recomienda probar borrados grandes después de cambios en el puente web
sin completar antes esta secuencia.
