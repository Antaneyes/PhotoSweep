# Privacidad

PhotoSweep está diseñada para funcionar sin backend propio, cuentas de
PhotoSweep, analítica, publicidad ni telemetría.

## Datos que maneja

- La autenticación se realiza dentro de la página oficial de Google cargada por
  GeckoView. PhotoSweep no recibe ni guarda la contraseña.
- Las cookies de Google permanecen en el almacenamiento privado de la
  aplicación en el dispositivo.
- El índice local contiene identificadores técnicos, URLs de miniaturas,
  dimensiones, fechas, duración y el estado de revisión de cada elemento.
- Las miniaturas precargadas se mantienen temporalmente en memoria.
- Android Backup y la transferencia de datos entre dispositivos están
  desactivados para los datos de la aplicación.

## Conexiones de red

En ejecución, la aplicación se conecta únicamente a dominios de Google
necesarios para iniciar sesión, consultar Google Photos, cargar contenido y
solicitar movimientos a la papelera. El proyecto no opera ningún servidor que
reciba fotografías, credenciales o decisiones.

El navegador interno se identifica en modo escritorio. Google puede describir
el acceso como procedente de Linux, aunque GeckoView se ejecute dentro del
teléfono Android.

## Borrado y conservación

Los swipes sólo modifican la base local. PhotoSweep solicita cambios en Google
Photos únicamente después de la confirmación de la cesta. Los elementos
aceptados se mueven a la papelera de Google Photos; la aplicación no la vacía.

Al borrar los datos de PhotoSweep o desinstalarla se eliminan la sesión, el
índice y las decisiones locales, pero no se modifican los elementos de Google
Photos.

## Código de terceros

La aplicación incorpora GeckoView, AndroidX, Jetpack Compose, Media3 y Coil.
Cada componente se rige por su propia licencia y puede aplicar sus propias
consideraciones de seguridad. Consulta los archivos de compilación para conocer
las versiones exactas.
