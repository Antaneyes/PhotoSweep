# PhotoSweep

PhotoSweep es una aplicación Android experimental y de código abierto para
revisar una fototeca de Google Photos con una interacción de tarjetas: deslizar
para conservar o añadir a una cesta y confirmar después su envío a la papelera.

La versión actual es **0.2.2**. Requiere Android 8.0 o posterior. La APK oficial
se distribuye para dispositivos `arm64-v8a`, la arquitectura habitual en
móviles Android modernos.

> [!WARNING]
> PhotoSweep no es un producto oficial de Google y utiliza endpoints web
> internos y no documentados. Google Photos puede cambiar sin previo aviso y
> romper la aplicación. Prueba siempre los borrados con contenido desechable y
> úsala bajo tu responsabilidad.

## Características

- Inicio de sesión de Google dentro de un perfil aislado de GeckoView.
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

## Privacidad y confianza

PhotoSweep no incluye backend, analítica, publicidad ni telemetría. La sesión de
Google, el índice y las decisiones se guardan en el almacenamiento privado de la
aplicación y las copias de seguridad de Android están desactivadas. Las
credenciales se introducen directamente en la página de Google cargada por
GeckoView; el código de PhotoSweep no las recibe ni las almacena.

El navegador interno usa modo escritorio porque la integración depende de la
web completa de Google Photos. Por ello, Google puede mostrar una alerta de
inicio de sesión desde **Linux**, aunque la aplicación se esté ejecutando en un
móvil Android. Consulta [PRIVACY.md](PRIVACY.md) para ver el flujo de datos
completo.

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

- es un proyecto experimental y sin garantía de funcionamiento;
- una actualización de Google Photos puede romper la integración;
- no debe presentarse ni publicarse como una integración oficial;
- su uso puede estar sujeto a las condiciones de Google;
- conviene probar cualquier versión nueva con elementos desechables.

## Requisitos

- JDK 21.
- Android SDK 36 o superior.
- Android 8.0 (API 26) o posterior.
- Arquitectura `arm64-v8a` para la APK oficial.
- Conexión a Internet.
- Una cuenta de Google con Google Photos.

La compilación y las dependencias son compatibles desde API 26. La ejecución
completa se ha verificado en un dispositivo `arm64-v8a` con Android 17; se
agradecen informes de prueba de versiones anteriores sin datos personales.

## Compilar y verificar

El proyecto incluye Gradle Wrapper:

```bash
./gradlew test lint assembleDebug
```

La APK resultante se genera en:

```text
app/build/outputs/apk/debug/app-debug.apk
```

GeckoView hace que la APK de depuración sea grande. Esta variante está limitada
a `arm64-v8a` de forma predeterminada para reducir parcialmente su tamaño.

GeckoView también incluye binarios para `armeabi-v7a` y `x86_64`. Quien
necesite una compilación de desarrollo para otra arquitectura puede indicarla
sin modificar el proyecto:

```bash
./gradlew assembleDebug -PtargetAbi=armeabi-v7a
./gradlew assembleDebug -PtargetAbi=x86_64
```

El workflow manual **Build other architectures** de GitHub Actions compila las
tres variantes debug como artefactos temporales. Las Releases oficiales siguen
publicando únicamente `arm64-v8a`.

La integración continua de GitHub ejecuta estos mismos controles, sin utilizar
ninguna clave privada.

### APK release firmada

Cada persona que distribuya la aplicación debe utilizar su propia clave. Copia
`signing.properties.example` como `signing.properties` y completa localmente:

```properties
storeFile=/ruta/segura/photosweep-release.jks
storePassword=...
keyAlias=photosweep
keyPassword=...
```

Tanto `signing.properties` como `*.jks` y `*.keystore` están ignorados por Git.
No subas nunca estos archivos ni sus contraseñas a GitHub, issues, Actions o
Releases.

Después se genera la APK firmada y optimizada con:

```bash
./gradlew clean assembleRelease
```

El archivo para distribuir queda en:

```text
app/build/outputs/apk/release/app-release.apk
```

La clave debe conservarse fuera del repositorio y con una copia de seguridad
segura: todas las futuras actualizaciones distribuidas necesitan la misma
firma. Una instalación `debug` no se puede actualizar directamente con una APK
`release` porque sus firmas son diferentes.

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

Durante el inicio de sesión, Google puede notificar un acceso desde Linux debido
al modo escritorio de GeckoView. Comprueba que la hora y ubicación aproximada
corresponden al propio dispositivo.

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

## Colaborar

Las contribuciones son bienvenidas. Lee [CONTRIBUTING.md](CONTRIBUTING.md) y no
incluyas fotografías, identificadores de cuenta, cookies, credenciales ni
capturas personales en issues o pull requests. Los problemas de seguridad deben
seguir el proceso de [SECURITY.md](SECURITY.md).

## Licencia

El código propio de PhotoSweep se distribuye bajo la [licencia MIT](LICENSE).
Google Photos y las dependencias de terceros conservan sus respectivas marcas,
licencias y condiciones. PhotoSweep no está afiliada, respaldada ni aprobada
por Google.
